/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.secdec.codepulse.tracer

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import org.joda.time.format.DateTimeFormat
import com.secdec.codepulse.data.trace._
import com.secdec.codepulse.pages.traces.TraceDetailsPage
import akka.actor.Cancellable
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Helpers.AsBoolean
import net.liftweb.util.Helpers.AsInt

class TraceAPIServer(manager: TraceManager) extends RestHelper with Loggable {

	import ActivityRequest._

	implicit class RichRecordingMetadata(rec: RecordingMetadata) {
		def toJson = {
			val idField = Some { JField("id", rec.id) }
			val runningField = Some { JField("running", rec.running) }
			val labelField = for (label <- rec.clientLabel) yield JField("label", label)
			val colorField = for (color <- rec.clientColor) yield JField("color", color)

			val fields = List(idField, runningField, labelField, colorField).flatten
			JObject(fields)
		}
	}

	def initializeServer() = {
		LiftRules.dispatch.append(TraceAPIServer.this)
		this
	}

	val accumulationRequestControllers = collection.mutable.Map.empty[TraceId, AccumulationRequestController]

	/*
	 * For a TracingTarget, initialize a "Controller" that can match requests for trace accumulation
	 * data to the actual data, delaying each request's response by up to 10 seconds if no data is
	 * available at the time the request is made.
	 * 
	 * When the Controller is created, set up a task that polls the trace data 3 times per second,
	 * putting it into the controller. Meanwhile, as API requests for the accumulation data come in,
	 * they are forwarded to the same Controller.
	 * 
	 * Note that targets may be deleted (removed from the trace manager), but there is no event to
	 * listen for this. As a workaround, check that the `target` is still in the trace manager before
	 * reading its data. If the target has been removed, cancel the polling task, so that there isn't
	 * an extra reference to the target that prevents it from being GC'd.
	 */
	def getAccumulationRequestController(target: TracingTarget) = accumulationRequestControllers getOrElseUpdate (target.id, {
		val actorSystem = manager.actorSystem
		val interval = 333.millis

		val controller = new AccumulationRequestController(actorSystem)
		var task: Option[Cancellable] = None

		val c: Cancellable = actorSystem.scheduler.schedule(interval, interval) {
			// ensure that the target is still managed...
			manager.getTrace(target.id) match {
				case None =>
					task foreach { _.cancel }

				case Some(_) =>
					target.transientData.getAndClearRecentlyEncounteredNodes { data =>
						controller.enterData(data)
					}
			}
		}

		task = Some(c)

		controller
	})

	/** Describes a means of converting a path to and from a piece of data.
	  * PathMatchers can be used as extractors within this server for handling
	  * REST requests, or to generate paths based on data (for example, a snippet
	  * wants to create a link to one of the paths defined in this server; it
	  * could use a PathMatcher to do the work).
	  */
	trait PathMatcher[T] {
		def unapply(path: List[String]): Option[T]
		def apply(value: T): List[String]

		def map[U](unapply: PartialFunction[T, U], apply: U => T): PathMatcher[U] = {
			val un = unapply.lift
			new MappedPathMatcher(this, un, apply)
		}

		def toHref(value: T) = apply(value).mkString("/", "/", "")
	}

	/** Implementation class for the PathMatcher's `map` function.
	  */
	protected class MappedPathMatcher[U, T](subject: PathMatcher[T], mapUnapply: T => Option[U], mapApply: U => T) extends PathMatcher[U] {
		def unapply(path: List[String]): Option[U] = {
			subject.unapply(path) flatMap { mapUnapply }
		}
		def apply(value: U): List[String] = {
			subject.apply(mapApply(value))
		}
	}

	/** PathMatcher that expects paths in the form `/trace-api/<trace.id>/whatever/else/later`.
	  * It looks up a TracingTarget with the given `trace.id` from this server's trace `manager`,
	  * extracting the target as well as the remaining path (`/whatever/else/later`).
	  */
	protected object TargetPath extends PathMatcher[(TracingTarget, List[String])] {
		def unapply(path: List[String]): Option[(TracingTarget, List[String])] = path match {
			case "trace-api" :: TraceId(traceId) :: tail => manager.getTrace(traceId) map { _ -> tail }
			case _ => None
		}
		def apply(ts: (TracingTarget, List[String])) = {
			"trace-api" :: ts._1.id.num.toString :: ts._2
		}
	}

	protected def simpleTargetPath(tail: String): PathMatcher[TracingTarget] = TargetPath.map[TracingTarget](
		{ case (target, List(`tail`)) => target },
		(_, List(tail)))

	/** Object that contains PathExtractor instances that can be used to parse or generate
	  * Paths with their respective bits of data.
	  */
	object Paths {
		/** /trace-api/<target.id>/start */
		val Start = simpleTargetPath("start")

		/** /trace-api/<target.id>/end */
		val End = simpleTargetPath("end")

		/** /trace-api/<target.id>/status */
		val Status = simpleTargetPath("status")

		/** /trace-api/<target.id>/export */
		val Export = simpleTargetPath("export")

		/** /trace-api/<target.id>/rename */
		val Rename = simpleTargetPath("rename")

		/** /trace-api/<target.id>/packageTree */
		val PackageTree = simpleTargetPath("packageTree")

		/** /trace-api/<target.id>/treemap */
		val Treemap = simpleTargetPath("treemap")

		val TreeInstrumentation = simpleTargetPath("tree-instrumentation")

		/** /trace-api/<target.id>/recordings */
		val Recordings = simpleTargetPath("recordings")

		/** /trace-api/<target.id>/recording */
		val NewRecording = simpleTargetPath("recording")

		/** /trace-api/<target.id>/coverage */
		val Coverage = simpleTargetPath("coverage")

		/** /trace-api/<target.id>/records */
		val Records = simpleTargetPath("records")

		/** /trace-api/<target.id>/accumulation */
		val Accumulation = simpleTargetPath("accumulation")

		/** /trace-api/<target.id>/recording/<recording.id> */
		val Recording = TargetPath.map[(TracingTarget, RecordingMetadata)](
			{
				case (target, List("recording", AsInt(recordingId))) if target.traceData.recordings.contains(recordingId) =>
					val rec = target.traceData.recordings.get(recordingId)
					(target, rec)
			},
			{ case (target, recording) => (target, List("recording", recording.id.toString)) })
	}

	private object DataPath {
		def unapply(path: List[String]): Option[(TraceData, List[String])] = {
			TargetPath.unapply(path) map {
				case (traceTarget, tail) => (traceTarget.traceData, tail)
			}
		}
	}

	private implicit def futureResponseToResponse(f: Future[LiftResponse]) = RestContinuation.async { callback =>
		f onComplete {
			case Success(response) => callback(response)
			case Failure(e) =>
				logger.error("Future response failed", e)
				callback(InternalServerErrorResponse())
		}
	}

	serve {

		// GET a list of traces
		case List("trace-api", "traces") Get req =>
			val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
			def prettyDate(d: Long) = dateFormat.print(d)
			val traces = manager.tracesIterator.toList.sortBy(_.id)

			val traceJsonFutures: List[Future[JObject]] = traces map { target =>
				val data = target.traceData
				val href = TraceDetailsPage.traceHref(target.id)

				for (traceState <- target.getState) yield ("id" -> target.id.num) ~
					("name" -> data.metadata.name) ~
					("hasCustomName" -> data.metadata.hasCustomName) ~
					("created" -> prettyDate(data.metadata.creationDate)) ~
					("imported" -> data.metadata.importDate.map(prettyDate)) ~
					("href" -> href) ~
					("exportHref" -> Paths.Export.toHref(target)) ~
					("deleteHref" -> TargetPath.toHref(target -> Nil)) ~
					("state" -> traceState.name)
			}

			Future.sequence(traceJsonFutures) map { JsonResponse(_) }

		// DELETE a trace
		case TargetPath(target, Nil) Delete req =>
			manager.removeTrace(target.id) match {
				case Some(removed) => OkResponse()
				case None => NotFoundResponse()
			}

		// POST a new tracer agent connection
		case Paths.Start(target) Post req =>
			target.requestNewTraceConnection()
			OkResponse()

		// POST the current trace to stop
		case Paths.End(target) Post req =>
			target.requestTraceEnd()
			OkResponse()

		// GET the current status of the trace
		case Paths.Status(target) Get req =>
			target.getState map { state => PlainTextResponse(state.name) }

		// GET a trace data export (as a .pulse file)
		case Paths.Export(target) Get req =>
			export.TraceExporter.exportResponse(target.traceData)

		// POST rename a trace
		// query: name=newName
		case Paths.Rename(target) Post req =>
			req.param("name").toOption match {
				case None => BadResponse()
				case Some(newName) =>
					target.traceData.metadata.name = newName

					// check for a name conflict
					val thisId = target.id
					val hasConflict = manager.tracesIterator
						.filterNot(_.id == thisId)
						.find(_.traceData.metadata.name == newName)
						.isDefined

					if (hasConflict) {
						JsonResponse(Map("warn" -> "nameConflict"))
					} else OkResponse()
			}

		// GET the trace's package tree as json
		case Paths.PackageTree(target) Get req =>
			PackageTreeStreamer.streamPackageTree(target.treeBuilder.packageTree)

		// stream a projected treemap based on the POSTed selected packages
		case Paths.Treemap(target) Post req =>
			req.param("nodes") match {
				case Full(packages) =>
					val ids = packages.split(',').flatMap(AsInt.unapply).toSet
					val tree = target.treeBuilder.projectTree(ids)
					TreemapDataStreamer.streamTreemapData(tree)

				case _ => BadResponse()
			}

		case Paths.TreeInstrumentation(target) Put req =>
			def getBool(j: JValue) = j match {
				case JInt(num) => Some(num > 0)
				case JBool(b) => Some(b)
				case _ => None
			}

			req.json.toOption match {
				case Some(json: JObject) => {
					for {
						JField(AsInt(key), rawValue) <- json.obj
						boolValue <- getBool(rawValue)
					} target.traceData.treeNodeData.updateTraced(key, boolValue)
				}
				case _ => BadResponse()
			}
			OkResponse()

		// GET a JSON listing of all of the custom recordings for a trace.
		case Paths.Recordings(target) Get req =>
			JsonResponse(target.traceData.recordings.all.map(_.toJson))

		// POST Add a new custom recording to a trace
		case Paths.NewRecording(target) Post req =>
			JsonResponse(target.traceData.recordings.create.toJson)

		// GET a recording's metadata as json
		case Paths.Recording(traceData, recording) Get req =>
			JsonResponse(recording.toJson)

		// POST changes to a recording's metadata
		// query: [color=newColor][label=newLabel][running=newRunning] (all optional)
		case Paths.Recording(target, recording) Post req =>
			println(s"update recording with params: ${req.params}")
			for (color <- req.param("color")) recording.clientColor = Some(color)
			for (label <- req.param("label")) recording.clientLabel = Some(label)
			for (AsBoolean(running) <- req.param("running")) recording.running = running
			OkResponse()

		// DELETE a recording
		case Paths.Recording(target, recording) Delete req =>
			target.traceData.recordings.remove(recording.id)
			OkResponse()

		// GET coverage counts for recordings in the trace
		// query: [recordingKey=returnKey]*
		// - see ActivityRequest for info on the expected request params
		// response: { returnKey: numMethodsCovered, ... }
		case Paths.Coverage(target) Get req =>
			val fieldsMap = for {
				(ar, respKey) <- parseActivityRequests(req.params)
			} yield respKey -> ar.lookup(target.traceData, target.transientData).size

			JsonResponse(fieldsMap)

		// GET coverage records for recordings in the trace
		// query: [recordingKey=returnKey]*
		//  - see ActivityRequest for info on the expected request params
		// response: { returnKey: [methodId, ...], ... }
		case Paths.Records(target) Get req =>
			val m = collection.mutable.Map.empty[Int, ListBuffer[String]]
			def buf(id: Int) = m.getOrElseUpdate(id, new ListBuffer)
			for {
				(ar, respKey) <- parseActivityRequests(req.params)
				methodId <- ar.lookup(target.traceData, target.transientData)
			} buf(methodId) += respKey

			val fields = for ((id, keys) <- m) yield JField(id.toString, keys.result)
			val json = JObject(fields.toList)
			JsonResponse(json)

		// GET recently traced method ids as a json array
		//  - list contains method ids that were traced since the last request to this URL
		case Paths.Accumulation(target) Get req =>
			RestContinuation.async { sendResponse =>
				val controller = getAccumulationRequestController(target)

				// make the request through the controller, so that it will respond when
				// it has some actual data (or if it waits too long to get the data)
				controller.makeRequest { accumulatedMethodIds =>
					val ids = accumulatedMethodIds.toList
					val json: JValue = ids
					val response = JsonResponse(json)
					sendResponse(response)
				}
			}
	}
}

sealed trait ActivityRequest {
	/** Look up the treemap node ids that correspond to this request
	  * within the given `traceData`.
	  *
	  * Returning `Iterable` leaves the door open for a quick O(1) `.size`
	  * call, instead of O(n) if the return type were `Iterator`.
	  */
	def lookup(traceData: TraceData, transientTraceData: TransientTraceData): Iterable[Int]
}

/** Provides parsing facilities for `ActivityRequest`s with a focus on HTTP request parameters.
  * ActivityRequest parameters are recognized in the form `<key>=<value>`, where the `<key>` is
  * a special name describing the desired activity, and `<value>` is a string that will be used
  * as the key in the returned result object.
  *
  * Example request data:
  * {{{
  * "recording/10" = "abc",
  * "recent/60000" = "last-minute",
  * "all" = "all"
  * }}}
  *
  * The result of requesting the above data would be in a form like:
  * {{{
  * "abc" = [list of methods recorded by recording id:10],
  * "last-minute" = [list of methods recorded in the last 60 seconds],
  * "all" = [list of all recorded methods]
  * }}}
  */
object ActivityRequest {

	case class RecordingActivity(recordingId: Int) extends ActivityRequest {
		def lookup(traceData: TraceData, transientTraceData: TransientTraceData) = {
			traceData.encounters.get(recordingId)
		}
	}
	case class RecentActivity(windowDuration: Int) extends ActivityRequest {
		def lookup(traceData: TraceData, transientTraceData: TransientTraceData) = {
			val thresholdTime = transientTraceData.now - windowDuration
			transientTraceData.getNodesEncounteredAfterTime(thresholdTime)
		}
	}
	case object AllActivity extends ActivityRequest {
		def lookup(traceData: TraceData, transientTraceData: TransientTraceData) = {
			traceData.encounters.get
		}
	}

	private val RecordingRegex = raw"recording/(\d+)".r
	private val RecentRegex = raw"recent/(\d+)".r
	private val AllRegex = "all"

	def parseActivityRequest(kv: (String, List[String])): Option[(ActivityRequest, String)] = {
		val requestKey = kv._1 match {
			case AllRegex => Some(AllActivity)
			case RecentRegex(AsInt(i)) => Some(RecentActivity(i))
			case RecordingRegex(AsInt(i)) => Some(RecordingActivity(i))
			case _ => None
		}

		val responseKey = kv._2 match {
			case head :: Nil => Some(head)
			case _ => None
		}

		for {
			req <- requestKey
			resp <- responseKey
		} yield req -> resp
	}

	def parseActivityRequests(params: Map[String, List[String]]): Map[ActivityRequest, String] = {
		params.flatMap { parseActivityRequest }
	}
}
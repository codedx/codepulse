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

import collection.mutable.{ Set => MutableSet, Map => MutableMap, ListBuffer }
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers.{ AsBoolean, AsInt, AsLong }
import net.liftweb.http.JsonResponse
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST._
import net.liftweb.http.NotFoundResponse
import net.liftweb.http.LiftResponse
import net.liftweb.http.LiftRules
import net.liftweb.http.OkResponse
import ActivityRequest._
import net.liftweb.http.rest.RestContinuation
import scala.util.Success
import net.liftweb.http.PlainTextResponse
import scala.util.Failure
import net.liftweb.http.InternalServerErrorResponse
import scala.concurrent.ExecutionContext.Implicits.global
import net.liftweb.http.BadResponse
import net.liftweb.http.InMemoryResponse
import java.text.SimpleDateFormat
import com.secdec.codepulse.pages.traces.TraceDetailsPage
import scala.concurrent.Future
import scala.concurrent.duration._
import language.implicitConversions
import akka.actor.Cancellable

class TraceAPIServer(manager: TraceManager) extends RestHelper {

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
					target.traceData.getAndClearRecentlyEncounteredNodes { data =>
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

		/** /trace-api/<target.id>/treemap */
		val Treemap = simpleTargetPath("treemap")

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
		val Recording = TargetPath.map[(TracingTarget, TraceRecording)](
			{
				case (target, List("recording", AsInt(recordingId))) if target.traceData.getRecording(recordingId).isDefined =>
					val rec = target.traceData.getRecording(recordingId).get
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

	private def stateToString(state: TracingTargetState): String = state match {
		case TracingTargetState.Idle => "idle"
		case TracingTargetState.Connecting => "connecting"
		case TracingTargetState.Running => "running"
		case TracingTargetState.Ending => "ending"
	}

	private implicit def futureResponseToResponse(f: Future[LiftResponse]) = RestContinuation.async { callback =>
		f onComplete {
			case Success(response) => callback(response)
			case Failure(_) => callback(InternalServerErrorResponse())
		}
	}

	serve {

		// GET a list of traces
		case List("trace-api", "traces") Get req =>
			val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
			def prettyDate(d: Long) = dateFormat.format(new java.util.Date(d))
			val traces = manager.tracesIterator.toList.sortBy(_.id)

			val traceJsonFutures: List[Future[JObject]] = traces map { target =>
				val data = target.traceData
				val href = TraceDetailsPage.traceHref(target.id)

				for (traceState <- target.getState) yield ("id" -> target.id.num) ~
					("name" -> data.name) ~
					("created" -> prettyDate(data.creationDate)) ~
					("imported" -> data.importDate.map(prettyDate)) ~
					("href" -> href) ~
					("exportHref" -> Paths.Export.toHref(target)) ~
					("deleteHref" -> TargetPath.toHref(target -> Nil)) ~
					("state" -> stateToString(traceState))
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
			target.getState map { state => PlainTextResponse(stateToString(state)) }

		// GET a trace data export (as a zip file)
		case Paths.Export(target) Get req =>
			RestContinuation.async { sendResponse =>
				manager.traceDataSaver.requestRawLoad(target.id) onComplete {
					case Failure(_) => sendResponse { InternalServerErrorResponse() }
					case Success(bytes) => sendResponse {
						val filename = s"trace-export.zip"
						val headers = List(
							"Content-Disposition" -> s"attachment; filename='$filename'",
							"Content-Type" -> "application/octet-stream")
						new InMemoryResponse(bytes, headers, cookies = Nil, code = 200)
					}
				}
			}

		// POST rename a trace
		// query: name=newName
		case Paths.Rename(target) Post req =>
			req.param("name").toOption match {
				case None => BadResponse()
				case Some(newName) =>
					target.traceData.name = newName

					// check for a name conflict
					val thisId = target.id
					val hasConflict = manager.tracesIterator
						.filterNot(_.id == thisId)
						.find(_.traceData.name == newName)
						.isDefined

					if (hasConflict) {
						JsonResponse(Map("warn" -> "nameConflict"))
					} else OkResponse()
			}

		// GET the trace's treemap data as json
		case Paths.Treemap(target) Get req =>
			val fields = target.traceData.treeNodes.map { _.asJsonField }
			JsonResponse(JObject(fields.toList))

		// GET a JSON listing of all of the custom recordings for a trace.
		case Paths.Recordings(target) Get req =>
			JsonResponse(target.traceData.recordings.map(_.toJson).toList)

		// POST Add a new custom recording to a trace
		case Paths.NewRecording(target) Post req =>
			JsonResponse(target.traceData.addNewRecording.toJson)

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
			target.traceData.markDirty()
			OkResponse()

		// DELETE a recording
		case Paths.Recording(target, recording) Delete req =>
			target.traceData.removeRecording(recording.id)
			OkResponse()

		// GET coverage counts for recordings in the trace
		// query: [recordingKey=returnKey]*
		// - see ActivityRequest for info on the expected request params
		// response: { returnKey: numMethodsCovered, ... }
		case Paths.Coverage(target) Get req =>
			val fieldsMap = for {
				(ar, respKey) <- parseActivityRequests(req.params)
			} yield respKey -> ar.lookup(target.traceData).size

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
				methodId <- ar.lookup(target.traceData)
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
	def lookup(traceData: TraceData): Iterable[Int]
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
		def lookup(traceData: TraceData) = {
			traceData.getNodesEncounteredByRecording(recordingId)
		}
	}
	case class RecentActivity(windowDuration: Int) extends ActivityRequest {
		def lookup(traceData: TraceData) = {
			val thresholdTime = traceData.now - windowDuration
			traceData.getNodesEncounteredAfterTime(thresholdTime)
		}
	}
	case object AllActivity extends ActivityRequest {
		def lookup(traceData: TraceData) = {
			traceData.getAllEncounteredNodes
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

/** Represents metadata associated with a "recording," which clients
  * may create and interact with through the REST api. A running trace
  * will associate any encountered methods with all TraceRecordings that
  * are known to the recorder and are currently running.
  *
  * @param id An identifier that should be used to distinguish this recording
  * from others. (It is the only criterea for `equals` and `hashCode`).
  */
class TraceRecording(val id: Int) {
	var running = true
	var clientLabel: Option[String] = None
	var clientColor: Option[String] = None

	override def equals(that: Any) = {
		if (that.isInstanceOf[TraceRecording]) that.asInstanceOf[TraceRecording].id == this.id
		else false
	}

	override def hashCode = id.hashCode

	def toJson = {
		val idField = Some { JField("id", id) }
		val runningField = Some { JField("running", running) }
		val labelField = for (label <- clientLabel) yield JField("label", label)
		val colorField = for (color <- clientColor) yield JField("color", color)

		val fields = List(idField, runningField, labelField, colorField).flatten
		JObject(fields)
	}

	override def toString = {
		val fields = List(
			Some(s"running:$running"),
			clientLabel.map { lb => s"label:$lb" },
			clientColor.map { c => s"color:$c" }).flatten.mkString(", ")
		s"TraceRecording($id){$fields}"
	}
}
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
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success

import org.joda.time.format.DateTimeFormat
import com.secdec.codepulse.userSettings
import com.secdec.codepulse.data.model._
import com.secdec.codepulse.dependencycheck.{ DependencyCheckReporter, JsonHelpers => DCJson }
import com.secdec.codepulse.surface.{ JsonHelpers => SDJson }
import com.secdec.codepulse.pages.traces.ProjectDetailsPage
import com.secdec.codepulse.tracer.snippet.{ ConnectionHelp, DotNETExecutableHelp, DotNETIISHelp }
import akka.actor.Cancellable
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Helpers.AsBoolean
import net.liftweb.util.Helpers.AsInt
import com.secdec.codepulse.components.notifications.Notifications
import com.secdec.codepulse.components.notifications.Notifications.NotificationId
import java.net.BindException
import java.util.Locale
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

import DCJson._
import SDJson._
import com.secdec.codepulse.data.storage.StorageManager
import com.secdec.codepulse.version

case class NodeSourceMetadata(nodeId: Int, nodeLabel: String, sourceFileId: Int, sourceFilePath: String, sourceLocationCount: Int, methodStartLine: Int)
case class NodeSourceFileContents(sourceFileId: Int, sourceFileContents: String)
case class NodeTracedSourceLocations(nodeId: Int, sourceLocations: List[SourceLocation])

class APIServer(manager: ProjectManager, treeBuilderManager: TreeBuilderManager) extends RestHelper with Loggable {

	implicit val executionContext = ExecutionContext fromExecutor Executors.newCachedThreadPool

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
		LiftRules.dispatch.append(APIServer.this)
		this
	}

	val accumulationRequestControllers = collection.mutable.Map.empty[ProjectId, AccumulationRequestController]

	/*
	 * For a TracingTarget, initialize a "Controller" that can match requests for trace accumulation
	 * data to the actual data, delaying each request's response by up to 10 seconds if no data is
	 * available at the time the request is made.
	 * 
	 * When the Controller is created, set up a task that polls the trace data 3 times per second,
	 * putting it into the controller. Meanwhile, as API requests for the accumulation data come in,
	 * they are forwarded to the same Controller.
	 * 
	 * Note that targets may be deleted (removed from the project manager), but there is no event to
	 * listen for this. As a workaround, check that the `target` is still in the project manager before
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
			manager.getProject(target.id) match {
				case None =>
					task foreach { _.cancel }

				case Some(_) =>
					target.transientData.nodeTraceData.getAndClearRecentlyEncountered { data =>
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

	/** PathMatcher that expects paths in the form `/api/<project.id>/whatever/else/later`.
	  * It looks up a TracingTarget with the given `project.id` from this server's project `manager`,
	  * extracting the target as well as the remaining path (`/whatever/else/later`).
	  */
	protected object TargetPath extends PathMatcher[(TracingTarget, List[String])] {
		def unapply(path: List[String]): Option[(TracingTarget, List[String])] = path match {
			case "api" :: ProjectId(projectId) :: tail => manager.getProject(projectId) map { _ -> tail }
			case _ => None
		}
		def apply(ts: (TracingTarget, List[String])) = {
			"api" :: ts._1.id.num.toString :: ts._2
		}
	}

	protected object InclusiveTargetPath extends PathMatcher[(TracingTarget, List[String])] {
		def unapply(path: List[String]): Option[(TracingTarget, List[String])] = path match {
			case "api" :: ProjectId(projectId) :: tail => manager.getInclusiveProject(projectId) map { _ -> tail }
			case _ => None
		}
		def apply(ts: (TracingTarget, List[String])) = {
			"api" :: ts._1.id.num.toString :: ts._2
		}
	}

	protected object NotificationPath extends PathMatcher[(NotificationId, List[String])] {
		def unapply(path: List[String]): Option[(NotificationId, List[String])] = path match {
			case "api" :: "notifications" :: AsInt(id) :: tail => Some(NotificationId(id) -> tail)
			case _ => None
		}
		def apply(ts: (NotificationId, List[String])) = {
			"api" :: "notifications" :: ts._1.num.toString :: ts._2
		}
	}

	protected def simpleTargetPath(tail: String): PathMatcher[TracingTarget] = TargetPath.map[TracingTarget](
		{ case (target, List(`tail`)) => target },
		(_, List(tail)))

	protected def simpleInclusiveTargetPath(tail: String): PathMatcher[TracingTarget] = InclusiveTargetPath.map[TracingTarget](
		{ case (target, List(`tail`)) => target },
		(_, List(tail)))

	protected def simpleNotificationPath(tail: String): PathMatcher[NotificationId] = NotificationPath.map[NotificationId](
		{ case (id, List(`tail`)) => id },
		(_, List(tail)))

	protected object AcknowledgmentPath extends PathMatcher[TraceConnectionAcknowledgment] {
		def unapply(path: List[String]): Option[TraceConnectionAcknowledgment] = path match {
			case "api" :: "connection" :: "accept" :: ProjectId(projectId) :: Nil =>
				manager.getProject(projectId) map { TraceConnectionAcknowledgment.Acknowledged(_) }
			case "api" :: "connection" :: "reject" :: Nil =>
				Some(TraceConnectionAcknowledgment.Rejected)
			case _ => None
		}
		def apply(ack: TraceConnectionAcknowledgment) = ack match {
			case TraceConnectionAcknowledgment.Acknowledged(target) =>
				"api" :: "connection" :: "accept" :: target.id.num.toString :: Nil
			case TraceConnectionAcknowledgment.Rejected =>
				"api" :: "connection" :: "reject" :: Nil
			case TraceConnectionAcknowledgment.Canceled =>
				throw new IllegalArgumentException("No path available for 'Canceled'")
		}
	}

	/** Object that contains PathExtractor instances that can be used to parse or generate
	  * Paths with their respective bits of data.
	  */
	object Paths {

		val Project = simpleTargetPath("project-data")

		/** /api/<target.id>/end */
		val End = simpleTargetPath("end")

		/** /api/<target.id>/status */
		val Status = simpleInclusiveTargetPath("status")

		/** /api/<target.id>/dcstatus */
		val DepCheckStatus = simpleTargetPath("dcstatus")

		/** /api/<target.id>/dcreport */
		val DepCheckReport = simpleTargetPath("dcreport")

		/** /api/<target.id>/sdstatus */
		val SurfaceDetectionStatus = simpleTargetPath("sdstatus")

		/** /api/<target.id>/export */
		val Export = simpleTargetPath("export")

		/** /api/<target.id>/rename */
		val Rename = simpleTargetPath("rename")

		/** /api/<target.id>/packageTree */
		val PackageTree = simpleTargetPath("packageTree")

		/** /api/<target.id>/vulnerableNodes */
		val VulnerableNodes = simpleTargetPath("vulnerableNodes")

		/** /api/<target.id>/treemap */
		val Treemap = simpleTargetPath("treemap")

		val TreeInstrumentation = simpleTargetPath("tree-instrumentation")

		/** /api/<target.id>/recordings */
		val Recordings = simpleTargetPath("recordings")

		/** /api/<target.id>/recording */
		val NewRecording = simpleTargetPath("recording")

		/** /api/<target.id>/coverage */
		val Coverage = simpleTargetPath("coverage")

		/** /api/<target.id>/records */
		val Records = simpleTargetPath("records")

		/** /api/<target.id>/accumulation */
		val Accumulation = simpleTargetPath("accumulation")

		/** /api/<target.id>/undo-delete */
		val UndoDelete = simpleTargetPath("undo-delete")

		/** /api/notifications/<note.id>/dismiss */
		val DismissNotification = simpleNotificationPath("dismiss")

		/** /api/<target.id>/recording/<recording.id> */
		val Recording = TargetPath.map[(TracingTarget, RecordingMetadata)](
			{
				case (target, List("recording", AsInt(recordingId))) if target.projectData.recordings.contains(recordingId) =>
					val rec = target.projectData.recordings.get(recordingId)
					(target, rec)
			},
			{ case (target, recording) => (target, List("recording", recording.id.toString)) })

		/** /api/connection/[reject|accept/<project.id>] */
		val Acknowledgment = AcknowledgmentPath

		val NodeSourceCodeMetadata = TargetPath.map[(TracingTarget, NodeSourceMetadata)]({
				case (target, listParts @ List("node", AsInt(nodeId), "source-metadata")) =>
					(for {
						node <- target.projectData.treeNodeData.getNode(nodeId)
						fileId <- node.sourceFileId
						sourceFile <- target.projectData.sourceData.getSourceFile(fileId)
					} yield {
						(target, NodeSourceMetadata(node.id, node.label, fileId, sourceFile.path, node.sourceLocationCount.getOrElse(0), node.methodStartLine.getOrElse(0)))
					}) getOrElse (target, NodeSourceMetadata(listParts(1).toInt, "", -1, "", 0, 0))
			}, {
				case (target, nodeSourceMetadata) =>
					(target, List("node", nodeSourceMetadata.nodeId.toString, "source-metadata"))
			}
		)

		val NodeSourceCodeFileContents = TargetPath.map[(TracingTarget, NodeSourceFileContents)]({
				case (target, listParts @ List("source", AsInt(sourceFileId))) =>
					(for {
						storage <- StorageManager.getStorageFor(target.projectData.id)
						sourceFile <- target.projectData.sourceData.getSourceFile(sourceFileId)
						content <- storage.loadEntry(sourceFile.path)
					} yield {
						storage.close
						(target, NodeSourceFileContents(sourceFileId, content))
					}) getOrElse (target, NodeSourceFileContents(listParts(1).toInt, "Source unavailable"))
			},
			{
				case (target, nodeSourceFileContents) =>
					(target, List("source", nodeSourceFileContents.sourceFileId.toString))
			}
		)

		val NodeTracedSourceCodeLocations = TargetPath.map[(TracingTarget, NodeTracedSourceLocations)](
			{
				case (target, listParts @ List("node", AsInt(nodeId), "source-locations")) =>
					val encounters = target.projectData.encounters
					val locations = (for {
						sourceLocations <- encounters.getTracedSourceLocations(nodeId)
					} yield {
						sourceLocations
					})
					(target, NodeTracedSourceLocations(listParts(1).toInt, locations))
			},
			{ case (target, nodeTracedSourceLocations) => (target, List("node", nodeTracedSourceLocations.nodeId.toString, "source-locations"))}
		)
	}

	private object DataPath {
		def unapply(path: List[String]): Option[(ProjectData, List[String])] = {
			TargetPath.unapply(path) map {
				case (traceTarget, tail) => (traceTarget.projectData, tail)
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

		// GET the Code Pulse version
		case List("api", "version") Get req =>
			JsonResponse("appVersion" -> version.number)

		// GET a list of projects
		case List("api", "projects") Get req =>
			val localDateFormatPattern = DateTimeFormat.patternForStyle("SS", Locale.getDefault)
			val dateFormat = DateTimeFormat.forPattern(localDateFormatPattern)
			def prettyDate(d: Long) = dateFormat.print(d)
			val projects = manager.projectsIterator.toList.sortBy(_.id)

			val projectJsonFutures: List[Future[JObject]] = projects map { target =>
				val data = target.projectData
				val href = ProjectDetailsPage.projectHref(target.id)

				for (traceState <- target.getState) yield ("id" -> target.id.num) ~
					("name" -> data.metadata.name) ~
					("hasCustomName" -> data.metadata.hasCustomName) ~
					("created" -> prettyDate(data.metadata.creationDate)) ~
					("imported" -> data.metadata.importDate.map(prettyDate)) ~
					("href" -> href) ~
					("exportHref" -> Paths.Export.toHref(target)) ~
					("deleteHref" -> TargetPath.toHref(target -> Nil)) ~
					("state" -> traceState.name) ~
					("dependencyCheck" -> data.metadata.dependencyCheckStatus.json) ~
					("surfaceDetector" -> data.metadata.surfaceDetectorStatus.json)
			}

			Future.sequence(projectJsonFutures) map { JsonResponse(_) }

		// GET a naming conflict with a given name
		case List("api", "check-name-conflict") Get req =>
			req.param("name").toOption match {
				case None => BadResponse()
				case Some(name) =>
					val hasConflict = manager.projectsIterator
						.filter {
							// don't count deleted or deleting projects for the name conflict
							_.getStateSync match {
								case Some(TracingTargetState.Deleted | TracingTargetState.DeletePending) => false
								case _ => true
							}
						}
						.find(_.projectData.metadata.name == name)
						.isDefined
					val responseText = String.valueOf(hasConflict)
					PlainTextResponse(responseText)
			}

		// POST to dismiss a notification
		case Paths.DismissNotification(noteId) Post req =>
			Notifications.dismissNotification(noteId)
			OkResponse()

		// GET the current agent port number
		case List("api", "agent-port") Get req =>
			PlainTextResponse(userSettings.tracePort.toString)

		// PUT a new agent port number
		case List("api", "agent-port") Put req =>
			req.param("port") match {
				case Full(AsInt(port)) =>
					if (port <= 0 || port > 65535) {
						PlainTextResponse("Invalid port number.", 500)
					} else if (userSettings.tracePort != port) {
						try {
							TraceServer.setPort(port)
							userSettings.tracePort = port
							OkResponse()
						} catch {
							case e: BindException if e.getMessage startsWith "Address already in use" =>
								PlainTextResponse(s"Port $port already in use.", 500)
							case e: BindException if e.getMessage startsWith "Permission denied" =>
								PlainTextResponse(s"Permission denied to use port $port.", 500)
							case _: Exception =>
								PlainTextResponse("Unknown error.", 500)
						}
					} else
						OkResponse()

				case _ => PlainTextResponse("Unknown error.", 500)
			}

		// GET the agent string
		case List("api", "agent-string") Get req =>
			PlainTextResponse(ConnectionHelp.traceAgentCommand)

		case List("api", "iis-agent-string") Get req =>
			PlainTextResponse(DotNETIISHelp.dotNETTraceCommandForIIS)

		case List("api", "executable-agent-string") Get req =>
			PlainTextResponse(DotNETExecutableHelp.dotNETTraceCommandForExecutable)

		// POST an acknowledgment of an agent connection
		case Paths.Acknowledgment(ack) Post req => ack match {
			case TraceConnectionAcknowledgment.Acknowledged(target) =>
				traceConnectionAcknowledger().acknowledgeCurrentTrace(target)
				OkResponse()
			case TraceConnectionAcknowledgment.Rejected =>
				traceConnectionAcknowledger().rejectCurrentTrace()
				OkResponse()
			case _ =>
				BadResponse()
		}

		// DELETE a project (actually schedules it for deletion later)
		case TargetPath(target, Nil) Delete req =>
			manager.removeProject(target)
			OkResponse()

		case Paths.Project(target) Get req =>
			val localDateFormatPattern = DateTimeFormat.patternForStyle("SS", Locale.getDefault)
			val dateFormat = DateTimeFormat.forPattern(localDateFormatPattern)
			def prettyDate(d: Long) = dateFormat.print(d)
			val data = target.projectData
			val href = ProjectDetailsPage.projectHref(target.id)

			val project = for (traceState <- target.getState) yield ("id" -> target.id.num) ~
				("name" -> data.metadata.name) ~
				("hasCustomName" -> data.metadata.hasCustomName) ~
				("created" -> prettyDate(data.metadata.creationDate)) ~
				("imported" -> data.metadata.importDate.map(prettyDate)) ~
				("href" -> href) ~
				("exportHref" -> Paths.Export.toHref(target)) ~
				("deleteHref" -> TargetPath.toHref(target -> Nil)) ~
				("state" -> traceState.name) ~
				("dependencyCheck" -> data.metadata.dependencyCheckStatus.json) ~
				("surfaceDetector" -> data.metadata.surfaceDetectorStatus.json)

			project.map(JsonResponse(_))


		// UNDO a project deletion (only works within a short time after requesting the delete)
		case Paths.UndoDelete(target) Post req =>
			manager
				.cancelProjectDeletion(target)
				.map { _ => OkResponse() }
				.recover { case e => new NotFoundResponse(e.getMessage) }

		// POST the current trace to stop
		case Paths.End(target) Post req =>
			target.requestTraceEnd()
			OkResponse()

		// GET the current status of the trace
		case Paths.Status(target) Get req =>
			target.getState map { state =>
				val status: JObject = ("name" -> state.name) ~ ("information" -> state.information)
				JsonResponse(status)
			}

		// GET the current dependency check status for the trace
		case Paths.DepCheckStatus(target) Get req =>
			JsonResponse(target.projectData.metadata.dependencyCheckStatus.json)

		// GET a dependency check report for the trace
		// query: nodes=<comma separated node IDs>
		case Paths.DepCheckReport(target) Get req =>
			req.param("nodes") match {
				case Full(nodes) =>
					JsonResponse {
						DependencyCheckReporter.buildReport(target.projectData, nodes.split(',').flatMap(AsInt.unapply))
					}
				case _ => BadResponse()
			}

		case Paths.SurfaceDetectionStatus(target) Get req =>
			JsonResponse(target.projectData.metadata.surfaceDetectorStatus.json)

		// GET the vulnerable nodes
		case Paths.VulnerableNodes(target) Get req =>
			def collectVulnNodes(node: PackageTreeNode): List[Int] = {
				val children = node.children.flatMap(collectVulnNodes)

				node.id match {
					case Some(id) if node.vulnerable getOrElse false => id :: children
					case _ => children
				}
			}
			val vulnNodes = treeBuilderManager.get(target.id).packageTree.flatMap(collectVulnNodes)
			JsonResponse(vulnNodes)

		// GET a project data export (as a .pulse file)
		case Paths.Export(target) Get req =>
			export.ProjectExporter.exportResponse(target.projectData)

		// POST rename a project
		// query: name=newName
		case Paths.Rename(target) Post req =>
			req.param("name").toOption match {
				case None => BadResponse()
				case Some(newName) =>
					target.projectData.metadata.name = newName

					// check for a name conflict
					val thisId = target.id
					val hasConflict = manager.projectsIterator
						.filterNot(_.id == thisId)
						.find(_.projectData.metadata.name == newName)
						.isDefined

					if (hasConflict) {
						JsonResponse(Map("warn" -> "nameConflict"))
					} else OkResponse()
			}

		// GET the project's package tree as json
		case Paths.PackageTree(target) Get req =>
			PackageTreeStreamer.streamPackageTree(treeBuilderManager.get(target.id).packageTree)

		// stream a projected treemap based on the POSTed selected packages
		case Paths.Treemap(target) Post req =>
			req.param("nodes") match {
				case Full(packages) =>
					val ids = packages.split(',').flatMap(AsInt.unapply).toSet
					val tree = treeBuilderManager.get(target.id).projectTree(ids)
					TreemapDataStreamer.streamTreemapData(target.projectData.treeNodeData, tree)

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
					} target.projectData.treeNodeData.updateTraced(key, boolValue)
				}
				case _ => BadResponse()
			}
			OkResponse()

		// GET a JSON listing of all of the custom recordings for a project.
		case Paths.Recordings(target) Get req =>
			JsonResponse(target.projectData.recordings.all.map(_.toJson))

		// POST Add a new custom recording to a project
		case Paths.NewRecording(target) Post req =>
			JsonResponse(target.projectData.recordings.create.toJson)

		// GET a recording's metadata as json
		case Paths.Recording(projectData, recording) Get req =>
			JsonResponse(recording.toJson)

		// POST changes to a recording's metadata
		// query: [color=newColor][label=newLabel][running=newRunning] (all optional)
		case Paths.Recording(target, recording) Post req =>
			logger.debug(s"update recording with params: ${req.params}")
			for (color <- req.param("color")) recording.clientColor = Some(color)
			for (label <- req.param("label")) recording.clientLabel = Some(label)
			for (AsBoolean(running) <- req.param("running")) recording.running = running
			OkResponse()

		// DELETE a recording
		case Paths.Recording(target, recording) Delete req =>
			target.projectData.recordings.remove(recording.id)
			OkResponse()

		// GET coverage counts for recordings in the project
		// query: [recordingKey=returnKey]*
		// - see ActivityRequest for info on the expected request params
		// response: { returnKey: numMethodsCovered, ... }
		case Paths.Coverage(target) Get req =>
			val fieldsMap = for {
				(ar, respKey) <- parseNodeActivityRequests(req.params)
			} yield respKey -> ar.lookup(target.projectData, target.transientData).size

			JsonResponse(fieldsMap)

		// GET coverage records for recordings in the project
		// query: [recordingKey=returnKey]*
		//  - see ActivityRequest for info on the expected request params
		// response: { returnKey: [methodId, ...], ... }
		case Paths.Records(target) Get req =>
			val m = collection.mutable.Map.empty[Int, ListBuffer[String]]
			def buf(id: Int) = m.getOrElseUpdate(id, new ListBuffer)
			for {
				(ar, respKey) <- parseNodeActivityRequests(req.params)
				methodId <- ar.lookup(target.projectData, target.transientData)
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

		case Paths.NodeSourceCodeMetadata(target, nodeSourceMetadata) Get req =>
			val json = ("nodeId" -> nodeSourceMetadata.nodeId) ~
				("sourceLabel" -> nodeSourceMetadata.nodeLabel) ~
				("sourceFileId", nodeSourceMetadata.sourceFileId) ~
				("sourceFilePath", nodeSourceMetadata.sourceFilePath) ~
				("sourceLocationCount", nodeSourceMetadata.sourceLocationCount) ~
				("methodStartLine", nodeSourceMetadata.methodStartLine)
			JsonResponse(json)

		case Paths.NodeSourceCodeFileContents(target, nodeSourceFileContents) Get req =>
			PlainTextResponse(nodeSourceFileContents.sourceFileContents)

		case Paths.NodeTracedSourceCodeLocations(target, tracedSourceLocations) Get req =>

			val m = collection.mutable.Map.empty[Int, ListBuffer[String]]
			def buf(id: Int) = m.getOrElseUpdate(id, new ListBuffer)
			for {
				(ar, respKey) <- parseSourceLocationActivityRequests(tracedSourceLocations.nodeId, req.params)
				methodId <- ar.lookup(target.projectData, target.transientData)
			} buf(methodId) += respKey

			val coverage = for ((id, keys) <- m) yield JField(id.toString, keys.result)
			val locations = for(l <- tracedSourceLocations.sourceLocations) yield ("id" -> l.id) ~
				("startLine" -> l.startLine) ~
				("startCharacter" -> l.startCharacter) ~
				("endLine" -> l.endLine) ~
				("endCharacter" -> l.endCharacter)

			JsonResponse(JObject(JField("sourceLocationCoverage", JObject(coverage.toList)) :: JField("sourceLocations", locations) :: Nil))
	}
}

sealed trait ActivityRequest {
	/** Look up the treemap node ids that correspond to this request
	  * within the given `projectData`.
	  *
	  * Returning `Iterable` leaves the door open for a quick O(1) `.size`
	  * call, instead of O(n) if the return type were `Iterator`.
	  */
	def lookup(projectData: ProjectData, transientTraceData: TransientTraceData): Iterable[Int]
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

	case class NodeRecordingActivity(recordingId: Int) extends ActivityRequest {
		def lookup(projectData: ProjectData, transientTraceData: TransientTraceData) = {
			projectData.encounters.getRecordingNodeEncountersSet(recordingId)
		}
	}
	case class NodeRecentActivity(windowDuration: Int) extends ActivityRequest {
		def lookup(projectData: ProjectData, transientTraceData: TransientTraceData) = {
			val thresholdTime = System.currentTimeMillis - windowDuration
			transientTraceData.nodeTraceData.getEncounteredAfterTime(thresholdTime)
		}
	}
	case object NodeAllActivity extends ActivityRequest {
		def lookup(projectData: ProjectData, transientTraceData: TransientTraceData) = {
			projectData.encounters.getAllNodeEncountersSet()
		}
	}

	case class SourceLocationRecordingActivity(nodeId: Int, recordingId: Int) extends ActivityRequest {
		def lookup(projectData: ProjectData, transientTraceData: TransientTraceData) = {
			projectData.encounters.getRecordingSourceLocationEncountersSet(recordingId, nodeId)
		}
	}
	case class SourceLocationRecentActivity(nodeId: Int, windowDuration: Int) extends ActivityRequest {
		def lookup(projectData: ProjectData, transientTraceData: TransientTraceData) = {

			val thresholdTime = System.currentTimeMillis - windowDuration
			val sourceLocationTraceData = transientTraceData.sourceLocationTraceData
			projectData.encounters.getAllSourceLocationEncountersSet(nodeId).filter(x => sourceLocationTraceData.isEncounteredAfterTime(x, thresholdTime))
		}
	}
	case class SourceLocationAllActivity(nodeId: Int) extends ActivityRequest {
		def lookup(projectData: ProjectData, transientTraceData: TransientTraceData) = {
			projectData.encounters.getAllSourceLocationEncountersSet(nodeId)
		}
	}

	private val RecordingRegex = raw"recording/(\d+)".r
	private val RecentRegex = raw"recent/(\d+)".r
	private val AllRegex = "all"

	def parseNodeActivityRequest(kv: (String, List[String])): Option[(ActivityRequest, String)] = {
		val requestKey = kv._1 match {
			case AllRegex => Some(NodeAllActivity)
			case RecentRegex(AsInt(i)) => Some(NodeRecentActivity(i))
			case RecordingRegex(AsInt(i)) => Some(NodeRecordingActivity(i))
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

	def parseNodeActivityRequests(params: Map[String, List[String]]): Map[ActivityRequest, String] = {
		params.flatMap(parseNodeActivityRequest)
	}

	def parseSourceLocationActivityRequest(nodeId: Int, kv: (String, List[String])): Option[(ActivityRequest, String)] = {
		val requestKey = kv._1 match {
			case AllRegex => Some(SourceLocationAllActivity(nodeId))
			case RecentRegex(AsInt(i)) => Some(SourceLocationRecentActivity(nodeId, i))
			case RecordingRegex(AsInt(i)) => Some(SourceLocationRecordingActivity(nodeId, i))
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

	def parseSourceLocationActivityRequests(nodeId: Int, params: Map[String, List[String]]): Map[ActivityRequest, String] = {
		params.flatMap(x => parseSourceLocationActivityRequest(nodeId, x))
	}
}
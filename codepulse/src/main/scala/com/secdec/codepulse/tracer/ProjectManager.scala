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

import scala.collection.mutable.{ Map => MutableMap }
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import com.secdec.codepulse.components.notifications.NotificationMessage
import com.secdec.codepulse.components.notifications.NotificationSettings
import com.secdec.codepulse.components.notifications.Notifications
import com.secdec.codepulse.data.jsp.JasperJspMapper
import com.secdec.codepulse.data.jsp.JspMapper
import com.secdec.codepulse.data.model.ProjectData
import com.secdec.codepulse.data.model.ProjectDataProvider
import com.secdec.codepulse.data.model.ProjectId

import akka.actor.ActorSystem
import bootstrap.liftweb.AppCleanup
import reactive.EventSource
import reactive.EventStream
import reactive.Observing

object ProjectManager {
	lazy val defaultActorSystem = {
		val sys = ActorSystem("ProjectManagerSystem")
		AppCleanup.addShutdownHook { () =>
			sys.shutdown()
			sys.awaitTermination()
			println("Shutdown ProjectManager's ActorSystem")
		}
		sys
	}
}

class ProjectManager(val actorSystem: ActorSystem) extends Observing {

	private val projects = MutableMap.empty[ProjectId, TracingTarget]
	private val dataProvider: ProjectDataProvider = projectDataProvider
	private val transientDataProvider: TransientTraceDataProvider = transientTraceDataProvider
	val projectListUpdates = new EventSource[Unit]

	/** Looks up a TracingTarget from the given `traceId` */
	def getProject(projectId: ProjectId): Option[TracingTarget] = projects.get(projectId)

	def projectsIterator: Iterator[TracingTarget] = projects.valuesIterator

	private var nextProjectNum = 0
	private val nextProjectNumLock = new Object {}
	private def getNextProjectId(): ProjectId = nextProjectNumLock.synchronized {
		var id = ProjectId(nextProjectNum)
		nextProjectNum += 1
		id
	}
	private def registerProjectId(id: ProjectId): Unit = nextProjectNumLock.synchronized {
		nextProjectNum = math.max(nextProjectNum, id.num + 1)
	}

	/** Creates and adds a new TracingTarget with the given `projectData`, and an
	  * automatically-selected ProjectId.
	  */
	def createProject(): ProjectId = {
		val projectId = getNextProjectId

		val data = dataProvider getProject projectId

		//TODO: make jspmapper configurable somehow
		registerProject(projectId, data, Some(JasperJspMapper(data.treeNodeData)))

		projectId
	}

	/** Creates a new TracingTarget based on the given `traceId` and `traceData`,
	  * and returns it after adding it to this TraceManager.
	  */
	private def registerProject(projectId: ProjectId, projectData: ProjectData, jspMapper: Option[JspMapper]) = {
		registerProjectId(projectId)

		val target = AkkaTracingTarget(actorSystem, projectId, projectData, transientDataProvider get projectId, jspMapper)
		projects.put(projectId, target)

		// cause a projectListUpdate when this project's name changes
		projectData.metadata.nameChanges ->> { projectListUpdates fire () }

		// also cause a projectListUpdate right now, since we're adding to the list
		projectListUpdates fire ()

		val subscriptionMade = target.subscribeToStateChanges { stateUpdates =>
			// trigger an update when the target state updates
			stateUpdates ->> { projectListUpdates fire () }

			// when the state becomes 'deleted', send a notification about it
			stateUpdates foreach {
				case TracingTargetState.DeletePending =>
					val projectName = projectData.metadata.name
					val undoHref = apiServer.Paths.UndoDelete.toHref(target)
					val msg = NotificationMessage.ProjectDeletion(projectName, undoHref)
					Notifications.enqueueNotification(msg, NotificationSettings.defaultDelayed(13000), persist = true)
				case _ =>
			}
		}

		// wait up to 1 second for the subscription to be acknowledged
		Await.ready(subscriptionMade, atMost = 1.second)

		target
	}

	def removeUnloadedProject(projectId: ProjectId): Option[TracingTarget] = {
		dataProvider removeProject projectId
		for (project <- projects remove projectId) yield {
			project.notifyLoadingFailed()
			project
		}

	}

	def scheduleProjectDeletion(project: TracingTarget) = {
		val (deletionKey, deletionFuture) = project.setDeletePending()

		// in 10 seconds, actually delete the project
		val finalizer = actorSystem.scheduler.scheduleOnce(15.seconds) {

			// Request the finalization of the project target's deletion.
			// Doing so returns a Future that will succeed if the target
			// transitioned to the Deleted state, or fail if the deletion
			// was canceled.
			project.finalizeDeletion(deletionKey) onComplete { result =>
				println(s"project.finalizeDeletion() finished with $result")
			}
		}

		deletionFuture onComplete {
			case Success(_) =>
				// actually perform the deletion at this point
				projects remove project.id
				dataProvider removeProject project.id
				projectListUpdates fire ()

			case Failure(e) =>
				// the deletion was probably canceled
				println(s"Deletion failed or maybe canceled. Message says '${e.getMessage}'")
				finalizer.cancel()
		}

		deletionFuture
	}

	def cancelProjectDeletion(project: TracingTarget) = {
		val ack = project.cancelPendingDeletion

		ack onComplete {
			case Success(_) =>
				// the cancel request was acknowledged
				val projectName = project.projectData.metadata.name
				val msg = NotificationMessage.ProjectUndeletion(projectName)
				Notifications.enqueueNotification(msg, NotificationSettings.defaultDelayed(3000), persist = false)
			case Failure(e) =>
				println(s"Canceling delete failed. Message says '${e.getMessage}'")
		}

		ack
	}

	/** For each tracing target, make sure all data has been flushed.
	  */
	def flushProjects = projects.values.foreach(_.projectData.flush)

	/* Initialization */

	// Load project data files that are stored by the save manager.
	for {
		id <- dataProvider.projectList
		data = dataProvider getProject id
	} {
		println(s"loaded project $id")
		//TODO: make jspmapper configurable somehow
		val target = registerProject(id, data, Some(JasperJspMapper(data.treeNodeData)))
		target.notifyLoadingFinished()
	}

	// Also make sure any dirty projects are saved when exiting
	AppCleanup.addPreShutdownHook { () =>
		flushProjects
		println("Flushed ProjectManager projects")
	}

}
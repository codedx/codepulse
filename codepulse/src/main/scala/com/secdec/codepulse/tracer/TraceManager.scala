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
import com.secdec.codepulse.data.trace.TraceData
import com.secdec.codepulse.data.trace.TraceDataProvider
import com.secdec.codepulse.data.trace.TraceId

import akka.actor.ActorSystem
import bootstrap.liftweb.AppCleanup
import reactive.EventSource
import reactive.EventStream
import reactive.Observing

object TraceManager {
	lazy val defaultActorSystem = {
		val sys = ActorSystem("TraceManagerSystem")
		AppCleanup.add { () => sys.shutdown() }
		sys
	}
}

class TraceManager(val actorSystem: ActorSystem) extends Observing {

	private val traces = MutableMap.empty[TraceId, TracingTarget]
	private val dataProvider: TraceDataProvider = traceDataProvider
	private val transientDataProvider: TransientTraceDataProvider = transientTraceDataProvider
	val traceListUpdates = new EventSource[Unit]

	/** Looks up a TracingTarget from the given `traceId` */
	def getTrace(traceId: TraceId): Option[TracingTarget] = traces.get(traceId)

	def tracesIterator: Iterator[TracingTarget] = traces.valuesIterator

	private var nextTraceNum = 0
	private val nextTraceNumLock = new Object {}
	private def getNextTraceId(): TraceId = nextTraceNumLock.synchronized {
		var id = TraceId(nextTraceNum)
		nextTraceNum += 1
		id
	}
	private def registerTraceId(id: TraceId): Unit = nextTraceNumLock.synchronized {
		nextTraceNum = math.max(nextTraceNum, id.num + 1)
	}

	/** Creates and adds a new TracingTarget with the given `traceData`, and an
	  * automatically-selected TraceId.
	  */
	def createTrace(): TraceId = {
		val traceId = getNextTraceId()

		val data = dataProvider getTrace traceId

		//TODO: make jspmapper configurable somehow
		registerTrace(traceId, data, Some(JasperJspMapper(data.treeNodeData)))

		traceId
	}

	/** Creates a new TracingTarget based on the given `traceId` and `traceData`,
	  * and returns it after adding it to this TraceManager.
	  */
	private def registerTrace(traceId: TraceId, traceData: TraceData, jspMapper: Option[JspMapper]) = {
		registerTraceId(traceId)

		val target = AkkaTracingTarget(actorSystem, traceId, traceData, transientDataProvider get traceId, jspMapper)
		traces.put(traceId, target)

		// cause a traceListUpdate when this trace's name changes
		traceData.metadata.nameChanges ->> { traceListUpdates fire () }

		// also cause a traceListUpdate right now, since we're adding to the list
		traceListUpdates fire ()

		val subscriptionMade = target.subscribeToStateChanges { stateUpdates =>
			// trigger an update when the target state updates
			stateUpdates ->> { traceListUpdates fire () }

			// when the state becomes 'deleted', send a notification about it
			stateUpdates foreach {
				case TracingTargetState.DeletePending =>
					val traceName = traceData.metadata.name
					val undoHref = traceAPIServer().Paths.UndoDelete.toHref(target)
					val msg = NotificationMessage.TraceDeletion(traceName, undoHref)
					Notifications.enqueueNotification(msg, NotificationSettings.defaultDelayed(13000), persist = true)
				case _ =>
			}
		}

		// wait up to 1 second for the subscription to be acknowledged
		Await.ready(subscriptionMade, atMost = 1.second)

		target
	}

	def removeUnloadedTrace(traceId: TraceId): Option[TracingTarget] = {
		dataProvider.removeTrace(traceId)
		for (trace <- traces remove traceId) yield {
			trace.notifyLoadingFailed()
			trace
		}

	}

	def scheduleTraceDeletion(trace: TracingTarget) = {
		val deletionFuture = trace.setDeletePending()

		// in 10 seconds, actually delete the trace
		actorSystem.scheduler.scheduleOnce(15.seconds) {

			// Request the finalization of the trace target's deletion.
			// Doing so returns a Future that will succeed if the target
			// transitioned to the Deleted state, or fail if the deletion
			// was canceled.
			trace.finalizeDeletion() onComplete { result =>
				println(s"trace.finalizeDeletion() finished with $result")
			}
		}

		deletionFuture onComplete {
			case Success(_) =>
				// actually perform the deletion at this point
				traces remove trace.id
				dataProvider removeTrace trace.id
				traceListUpdates fire ()

			case Failure(e) =>
				// the deletion was probably canceled
				println(s"Deletion failed or maybe canceled. Message says '${e.getMessage}'")
		}

		deletionFuture
	}

	def cancelTraceDeletion(trace: TracingTarget) = {
		val ack = trace.cancelPendingDeletion()

		ack onComplete {
			case Success(_) =>
				// the cancel request was acknowledged
				val traceName = trace.traceData.metadata.name
				val msg = NotificationMessage.TraceUndeletion(traceName)
				Notifications.enqueueNotification(msg, NotificationSettings.defaultDelayed(3000), persist = false)
			case Failure(e) =>
				println(s"Canceling delete failed. Message says '${e.getMessage}'")
		}

		ack
	}

	/** For each tracing target, make sure all data has been flushed.
	  */
	def flushTraces = traces.values.foreach(_.traceData.flush)

	/* Initialization */

	// Load trace data files that are stored by the save manager.
	for {
		id <- dataProvider.traceList
		data = dataProvider.getTrace(id)
	} {
		println(s"loaded trace $id")
		//TODO: make jspmapper configurable somehow
		val target = registerTrace(id, data, Some(JasperJspMapper(data.treeNodeData)))
		target.notifyLoadingFinished()
	}

	// Also make sure any dirty traces are saved when exiting
	AppCleanup.add { () => flushTraces }

}
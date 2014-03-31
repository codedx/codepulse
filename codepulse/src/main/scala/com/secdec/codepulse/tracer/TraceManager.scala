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

import akka.actor.ActorSystem
import collection.mutable.{ Map => MutableMap }
import akka.actor.ActorRef
import akka.actor.Props
import reactive.EventStream
import bootstrap.liftweb.AppCleanup
import java.io.File
import concurrent.duration._
import net.liftweb.util.Helpers.AsInt
import reactive.Observing
import reactive.EventSource

object TraceManager {
	lazy val defaultActorSystem = {
		val sys = ActorSystem("TraceManagerSystem")
		AppCleanup.add { () => sys.shutdown() }
		sys
	}

	lazy val defaultStorageDir = {
		val basePath = com.secdec.codepulse.paths.appData
		val dir = new File(basePath, "codepulse-traces")
		dir.mkdirs
		dir
	}

	lazy val default = new TraceManager(defaultActorSystem, defaultStorageDir)
}

class TraceManager(val actorSystem: ActorSystem, storageDir: File) extends Observing {

	private val traces = MutableMap.empty[TraceId, TracingTarget]
	private val saveManager = new TraceDataSaveManager(storageDir)
	val traceListUpdates = new EventSource[Unit]

	val traceDataSaver = TraceDataSaveActor(actorSystem, saveManager)

	/** Looks up a TracingTarget from the given `traceId` */
	def getTrace(traceId: TraceId): Option[TracingTarget] = traces.get(traceId)

	def tracesIterator: Iterator[TracingTarget] = traces.valuesIterator

	/** Creates and adds a new TracingTarget with the given `traceData`, and an
	  * automatically-selected TraceId.
	  */
	def addTrace(traceData: TraceData): TracingTarget = {
		val traceId =
			if (traces.isEmpty) TraceId(0)
			else traces.keysIterator.max + 1

		addTrace(traceId, traceData)
	}

	/** Creates a new TracingTarget based on the given `traceId` and `traceData`,
	  * and returns it after adding it to this TraceManager.
	  */
	def addTrace(traceId: TraceId, traceData: TraceData): TracingTarget = {
		val target = AkkaTracingTarget(actorSystem, traceId, traceData)
		traces.put(traceId, target)
		traceData.markDirty() // so it will be saved soon

		// cause a traceListUpdate when this trace's name changes
		traceData.nameChanges ->> { traceListUpdates fire () }

		// also cause a traceListUpdate right now, since we're adding to the list
		traceListUpdates fire ()

		// trigger an update when the target state updates
		target.subscribe { stateUpdates =>
			stateUpdates ->> { traceListUpdates fire () }
		}

		target
	}

	def removeTrace(traceId: TraceId): Option[TracingTarget] = {
		for (trace <- traces remove traceId) yield {
			saveManager.delete(traceId)
			traceListUpdates fire ()
			trace
		}
	}

	/** For each tracing target, if its data has been marked as dirty,
	  * save the data and clear the dirtiness flag.
	  */
	def saveDirtyTraces = for { (id, target) <- traces } {
		val data = target.traceData
		data.clearDirtyWith {
			traceDataSaver.requestSave(id, data)
		}
	}

	/* Initialization */ {
		// Load trace data files that are stored by the save manager.
		for {
			id <- saveManager.listStoredIds
			data <- saveManager.load(id)
		} {
			println(s"loaded trace $id")
			addTrace(id, data)

			// addTrace automatically marks the data as dirty, but this is
			// a special case, where the data is fresh off the disk.
			data.clearDirtyWith()
		}

		// Schedule an auto-save request 3 times a second
		import actorSystem.dispatcher
		actorSystem.scheduler.schedule(333.millis, 333.millis) { saveDirtyTraces }

		// Also make sure any dirty traces are saved when the JVM goes down
		Runtime.getRuntime.addShutdownHook(new Thread {
			override def run = { saveDirtyTraces }
		})
	}

}
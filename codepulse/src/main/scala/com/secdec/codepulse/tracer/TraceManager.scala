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
import com.secdec.codepulse.data.trace.TraceId
import com.secdec.codepulse.data.trace.TraceDataProvider
import com.secdec.codepulse.data.trace.TraceData

object TraceManager {
	lazy val defaultActorSystem = {
		val sys = ActorSystem("TraceManagerSystem")
		AppCleanup.add { () => sys.shutdown() }
		sys
	}

	lazy val default = new TraceManager(defaultActorSystem)
}

class TraceManager(actorSystem: ActorSystem) extends Observing {

	private val traces = MutableMap.empty[TraceId, TracingTarget]
	private val dataProvider: TraceDataProvider = traceDataProvider
	private val transientDataProvider: TransientTraceDataProvider = transientTraceDataProvider
	val traceListUpdates = new EventSource[Unit]

	/** Looks up a TracingTarget from the given `traceId` */
	def getTrace(traceId: TraceId): Option[TracingTarget] = traces.get(traceId)

	def tracesIterator: Iterator[TracingTarget] = traces.valuesIterator

	/** Creates and adds a new TracingTarget with the given `traceData`, and an
	  * automatically-selected TraceId.
	  */
	def createTrace(): TraceId = {
		val traceId =
			if (traces.isEmpty) TraceId(0)
			else traces.keysIterator.max + 1

		registerTrace(traceId, dataProvider getTrace traceId)

		traceId
	}

	/** Creates a new TracingTarget based on the given `traceId` and `traceData`,
	  * and returns it after adding it to this TraceManager.
	  */
	private def registerTrace(traceId: TraceId, traceData: TraceData) {
		val target = AkkaTracingTarget(actorSystem, traceId, traceData, transientDataProvider get traceId)
		traces.put(traceId, target)

		// cause a traceListUpdate when this trace's name changes
		traceData.metadata.nameChanges ->> { traceListUpdates fire () }

		// also cause a traceListUpdate right now, since we're adding to the list
		traceListUpdates fire ()

		// trigger an update when the target state updates
		target.subscribe { stateUpdates =>
			stateUpdates ->> { traceListUpdates fire () }
		}
	}

	def removeTrace(traceId: TraceId): Option[TracingTarget] = {
		for (trace <- traces remove traceId) yield {
			dataProvider.removeTrace(traceId)
			traceListUpdates fire ()
			trace
		}
	}

	/** For each tracing target, make sure all data has been flushed.
	  */
	def flushTraces = traces.values.foreach(_.traceData.flush)

	/* Initialization */ {
		// Load trace data files that are stored by the save manager.
		for {
			id <- dataProvider.traceList
			data = dataProvider.getTrace(id)
		} {
			println(s"loaded trace $id")
			registerTrace(id, data)
		}

		// Also make sure any dirty traces are saved when the JVM goes down
		Runtime.getRuntime.addShutdownHook(new Thread {
			override def run = flushTraces
		})
	}

}
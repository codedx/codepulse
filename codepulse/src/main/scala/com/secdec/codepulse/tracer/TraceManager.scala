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
import com.secdec.codepulse.data.jsp.JspMapper
import com.secdec.codepulse.data.jsp.JasperJspMapper
import com.secdec.codepulse.data.trace.TraceId
import com.secdec.codepulse.data.trace.TraceDataProvider
import com.secdec.codepulse.data.trace.TraceData
import com.secdec.codepulse.data.trace.TreeBuilder

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

		val treeBuilder = new TreeBuilder(traceData.treeNodeData)

		val target = AkkaTracingTarget(actorSystem, traceId, traceData, transientDataProvider get traceId, treeBuilder, jspMapper)
		traces.put(traceId, target)

		// cause a traceListUpdate when this trace's name changes
		traceData.metadata.nameChanges ->> { traceListUpdates fire () }

		// also cause a traceListUpdate right now, since we're adding to the list
		traceListUpdates fire ()

		// trigger an update when the target state updates
		target.subscribeToStateChanges { stateUpdates =>
			stateUpdates ->> { traceListUpdates fire () }
		}

		target
	}

	def removeTrace(traceId: TraceId): Option[TracingTarget] = {
		for (trace <- traces remove traceId) yield {
			dataProvider.removeTrace(traceId)
			traceListUpdates fire ()
			trace.requestDeletion()
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
			//TODO: make jspmapper configurable somehow
			val target = registerTrace(id, data, Some(JasperJspMapper(data.treeNodeData)))
			target.notifyFinishedLoading()
		}

		// Also make sure any dirty traces are saved when exiting
		AppCleanup.add { () => flushTraces }
	}

}
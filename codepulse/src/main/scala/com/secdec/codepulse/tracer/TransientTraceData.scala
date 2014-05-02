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

import com.secdec.codepulse.data.model.ProjectId

/** Holds trace ID */
trait HasProjectId {
	def id: ProjectId
}

/** Keeps track of last encounter time by node ID */
trait HasTimedEncounterData {
	val latestEncounterTimesByNodeId = collection.mutable.Map.empty[Int, Long]

	@inline def now = System.currentTimeMillis

	def setLatestNodeEncounterTime(nodeId: Int) {
		setLatestNodeEncounterTime(nodeId, now)
	}

	def setLatestNodeEncounterTime(nodeId: Int, timestamp: Long) {
		latestEncounterTimesByNodeId.update(nodeId, timestamp)
	}

	def getNodesEncounteredAfterTime(time: Long): Iterable[Int] = {
		for {
			(nodeId, encounterTime) <- latestEncounterTimesByNodeId
			if encounterTime > time
		} yield nodeId
	}
}

/** Keeps track of recently encountered nodes */
trait HasAccumulatingEncounterData {
	val recentlyEncounteredNodes = Set.newBuilder[Int]

	def addRecentlyEncounteredNode(nodeId: Int) {
		recentlyEncounteredNodes.synchronized {
			recentlyEncounteredNodes += nodeId
		}
	}

	def addRecentlyEncounteredNodes(nodeIds: Traversable[Int]) {
		recentlyEncounteredNodes.synchronized {
			recentlyEncounteredNodes ++= nodeIds
		}
	}

	def getAndClearRecentlyEncounteredNodes[T](f: Traversable[Int] => T): T = recentlyEncounteredNodes.synchronized {
		val result = f(recentlyEncounteredNodes.result)
		recentlyEncounteredNodes.clear
		result
	}
}

/** Provides storage for transient trace data, i.e., data that we want to keep track of
  * but don't care about saving anyplace.
  *
  * @author robertf
  */
class TransientTraceData(val id: ProjectId) extends HasProjectId with HasTimedEncounterData with HasAccumulatingEncounterData {
	def addEncounter(nodeId: Int) {
		setLatestNodeEncounterTime(nodeId)
		addRecentlyEncounteredNode(nodeId)
	}
}

/** Keeps track of TransientTraceData instances.
  *
  * @author robertf
  */
class TransientTraceDataProvider {
	private val projects = collection.mutable.Map.empty[ProjectId, TransientTraceData]

	def get(id: ProjectId) = projects.getOrElseUpdate(id, new TransientTraceData(id))
}
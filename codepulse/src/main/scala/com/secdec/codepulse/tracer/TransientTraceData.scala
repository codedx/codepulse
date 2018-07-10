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

/** Keeps track of last encounter time by ID */
trait HasTimedEncounterData {
	val latestEncounterTimesById = collection.mutable.Map.empty[Int, Long]

	def setLatestEncounterTime(id: Int) {
		setLatestEncounterTime(id, System.currentTimeMillis)
	}

	def setLatestEncounterTime(id: Int, timestamp: Long) {
		latestEncounterTimesById.update(id, timestamp)
	}

	def getEncounteredAfterTime(time: Long): Iterable[Int] = {
		for {
			(id, encounterTime) <- latestEncounterTimesById
			if encounterTime > time
		} yield id
	}

	def isEncounteredAfterTime(id: Int, time: Long): Boolean = {
		val encounter = latestEncounterTimesById.get(id)
		if (encounter.isEmpty) false else encounter.get > time
	}
}

/** Keeps track of recently encountered nodes */
trait HasAccumulatingEncounterData {
	val recentlyEncountered = Set.newBuilder[Int]

	def addRecentlyEncountered(id: Int) {
		recentlyEncountered.synchronized {
			recentlyEncountered += id
		}
	}

	def addRecentlyEncountered(ids: Traversable[Int]) {
		recentlyEncountered.synchronized {
			recentlyEncountered ++= ids
		}
	}

	def getAndClearRecentlyEncountered[T](f: Traversable[Int] => T): T = recentlyEncountered.synchronized {
		val result = f(recentlyEncountered.result)
		recentlyEncountered.clear
		result
	}
}

class TransientNodeTraceData extends HasTimedEncounterData with HasAccumulatingEncounterData {
	def addEncounter(id: Int) {
		setLatestEncounterTime(id)
		addRecentlyEncountered(id)
	}
}

class TransientSourceLocationTraceData extends HasTimedEncounterData {
	def addEncounter(id: Int) {
		setLatestEncounterTime(id)
	}
}

/** Provides storage for transient trace data, i.e., data that we want to keep track of
  * but don't care about saving anyplace.
  *
  * @author robertf
  */
class TransientTraceData(val id: ProjectId) extends HasProjectId {
	val nodeTraceData = new TransientNodeTraceData()
	val sourceLocationTraceData = new TransientSourceLocationTraceData()
}

/** Keeps track of TransientTraceData instances.
  *
  * @author robertf
  */
class TransientTraceDataProvider {
	private val projects = collection.mutable.Map.empty[ProjectId, TransientTraceData]

	def get(id: ProjectId) = projects.getOrElseUpdate(id, new TransientTraceData(id))
}
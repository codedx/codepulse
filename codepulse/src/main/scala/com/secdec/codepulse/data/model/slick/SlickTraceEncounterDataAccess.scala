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

package com.secdec.codepulse.data.model.slick

import scala.concurrent.duration.FiniteDuration
import scala.slick.jdbc.JdbcBackend.Database
import akka.actor.ActorSystem
import com.secdec.codepulse.data.model.TraceEncounterDataAccess
import com.secdec.codepulse.util.TaskScheduler

/** Slick-backed TraceEncoutnerDataAccess implementation.
  * Buffers encounter data and batches database writes for efficiency. The buffering
  * interaction is done in a thread-safe manner.
  *
  * @author robertf
  */
private[slick] class SlickTraceEncounterDataAccess(dao: EncountersDao, db: Database, bufferSize: Int, flushInterval: FiniteDuration, actorSystem: ActorSystem) extends TraceEncounterDataAccess {
	private val flusher = TaskScheduler(actorSystem, flushInterval) {
		if (!pendingRecordingEncounters.isEmpty || !pendingUnassociatedEncounters.isEmpty)
			bufferLock.synchronized { flushPreLocked }
	}

	// lazily load
	private lazy val (recordingEncounters, unassociatedEncounters) = {
		val recordingEncounters = collection.mutable.HashMap.empty[Int, collection.mutable.Set[Int]]
		val unassociatedEncounters = collection.mutable.HashSet.empty[Int]

		db withSession { implicit session =>
			dao.iterateWith {
				_.foreach {
					case (Some(recordingId), nodeId) => recordingEncounters.getOrElseUpdate(recordingId, collection.mutable.HashSet.empty) += nodeId
					case (None, nodeId) => unassociatedEncounters += nodeId
				}
			}
		}

		(recordingEncounters, unassociatedEncounters)
	}

	private val bufferLock = new Object
	private val pendingRecordingEncounters = collection.mutable.ListBuffer.empty[(Int, Int)]
	private val pendingUnassociatedEncounters = collection.mutable.ListBuffer.empty[Int]

	private def flushPreLocked() {
		// this is to be called when locking has already been done for us
		db withTransaction { implicit transaction =>
			val associated = pendingRecordingEncounters.toIterable.map {
				case (recordingId, nodeId) => Some(recordingId) -> nodeId
			}
			val unassociated = pendingUnassociatedEncounters.toIterable.map {
				nodeId => None -> nodeId
			}

			dao.store(associated ++ unassociated)
		}

		pendingRecordingEncounters.clear
		pendingUnassociatedEncounters.clear
	}

	/** Flush anything cached for writing out to the db */
	def flush() {
		flusher.trigger
	}

	def close() {
		flusher.stop(true)
	}

	private def flushIfFullPreLocked() {
		if ((pendingRecordingEncounters.size + pendingUnassociatedEncounters.size) > bufferSize)
			flushPreLocked
	}

	def record(recordings: List[Int], encounteredNodes: List[Int]) {
		bufferLock.synchronized {
			recordings match {
				case Nil =>
					val additions = encounteredNodes.filterNot(unassociatedEncounters.contains)
					pendingUnassociatedEncounters ++= additions
					unassociatedEncounters ++= additions

				case recordings =>
					for {
						nodeId <- encounteredNodes

						recordingId <- recordings
						recording = recordingEncounters.getOrElseUpdate(recordingId, collection.mutable.HashSet.empty)

						if !(recording contains nodeId)
					} {
						pendingRecordingEncounters += recordingId -> nodeId
						recording += nodeId
					}
			}

			flushIfFullPreLocked
		}

		flusher.start
	}

	def get(): Set[Int] = // get all encounters
		bufferLock.synchronized { (recordingEncounters.flatMap(_._2) ++ unassociatedEncounters).toSet }

	def get(recordingId: Int): Set[Int] = // get encounters for one recording
		bufferLock.synchronized { recordingEncounters.getOrElse(recordingId, Nil).toSet }
}
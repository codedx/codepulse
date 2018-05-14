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
		val recordingEncounters = collection.mutable.HashMap.empty[Int, collection.mutable.HashMap[Int, collection.mutable.Set[Option[Int]]]]
		val unassociatedEncounters = collection.mutable.HashMap.empty[Int, collection.mutable.Set[Option[Int]]]

		db withSession { implicit session =>
			dao.iterateWith {
				_.foreach {
					case (Some(recordingId), nodeId, Some(sourceLocationId)) => {
						val recordingMap = recordingEncounters.getOrElseUpdate(recordingId, collection.mutable.HashMap.empty)
						val sourceLocationMap = recordingMap.getOrElseUpdate(nodeId, collection.mutable.Set.empty)
						sourceLocationMap += Some(sourceLocationId)
					}
					case (Some(recordingId), nodeId, None) => {
						val recordingMap = recordingEncounters.getOrElseUpdate(recordingId, collection.mutable.HashMap.empty)
						val sourceLocationMap = recordingMap.getOrElseUpdate(nodeId, collection.mutable.Set.empty)
						sourceLocationMap += None
					}
					case (None, nodeId, Some(sourceLocationId)) => {
						val sourceLocationMap = unassociatedEncounters.getOrElseUpdate(nodeId, collection.mutable.Set.empty)
						sourceLocationMap += Some(sourceLocationId)
					}
					case (None, nodeId, None) => {
						val sourceLocationMap = unassociatedEncounters.getOrElseUpdate(nodeId, collection.mutable.Set.empty)
						sourceLocationMap += None
					}
				}
			}
		}

		(recordingEncounters, unassociatedEncounters)
	}

	private val bufferLock = new Object
	private val pendingRecordingEncounters = collection.mutable.ListBuffer.empty[(Int, (Int, Option[Int]))]
	private val pendingUnassociatedEncounters = collection.mutable.ListBuffer.empty[(Int, Option[Int])]

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

	def record(recordings: List[Int], encounteredNodes: List[(Int, Option[Int])]) {
		bufferLock.synchronized {
			recordings match {
				case Nil =>
					val additions = encounteredNodes.filterNot(x => unassociatedEncounters.contains(x._1) && unassociatedEncounters(x._1).contains(x._2))
					pendingUnassociatedEncounters ++= additions

					additions.map(x => {
						val set = unassociatedEncounters.getOrElseUpdate(x._1, collection.mutable.Set.empty)
						set += x._2
					})

				case recordings =>
					for {
						recordingId <- recordings
						(nodeId, sourceLocationId) <- encounteredNodes

						recording = recordingEncounters.getOrElseUpdate(recordingId, collection.mutable.HashMap.empty)

						if !recording.contains(nodeId) || !(recording.get(nodeId)).get.contains(sourceLocationId)
					} {
						pendingRecordingEncounters += recordingId -> (nodeId, sourceLocationId)

						val sourceLocationSet = recording.getOrElseUpdate(nodeId, collection.mutable.Set.empty)
						sourceLocationSet += sourceLocationId
					}
			}

			flushIfFullPreLocked
		}

		flusher.start
	}

	def getAllEncounters(): List[(Int, Option[Int])] = // get all encounters
		bufferLock.synchronized {
			var encounterList = collection.mutable.ListBuffer.empty[(Int, Option[Int])]
			for {
				nodeId <- unassociatedEncounters.keys
				sourceLocationId <- unassociatedEncounters.get(nodeId).get
			}
			{
				val sourceLocationEncounter = (nodeId, sourceLocationId)
				encounterList += sourceLocationEncounter
			}
			encounterList ++= recordingEncounters.keys.flatMap(x => getRecordingEncounters(x))
			encounterList.toSet.toList
		}

	def getAllNodeEncountersSet(): Set[Int] = // get all encounters
		bufferLock.synchronized {
			(recordingEncounters.flatMap(_._2.keys) ++ unassociatedEncounters.keys).toSet
		}

	def getRecordingEncounters(recordingId: Int): List[(Int, Option[Int])] = // get encounters for one recording
		bufferLock.synchronized {
			var encounterList = collection.mutable.ListBuffer.empty[(Int, Option[Int])]
			val nodeMap = recordingEncounters.get(recordingId)
			if (nodeMap == None) {
				return encounterList.toList
			}

			for {
				nodeId <- nodeMap.get.keys
				sourceLocationId <- nodeMap.get(nodeId)
			}
			{
				val sourceLocationEncounter = (nodeId, sourceLocationId)
				encounterList += sourceLocationEncounter
			}
			encounterList.toList
		}

	def getRecordingNodeEncountersSet(recordingId: Int): Set[Int] = // get encounters for one recording
		bufferLock.synchronized {
			val encounters = recordingEncounters.getOrElse(recordingId, Nil)
			if (encounters == Nil) { return Nil.toSet }
			encounters.map(_._1).toSet
		}
}
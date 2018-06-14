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

import scala.slick.driver.JdbcProfile
import scala.slick.model.ForeignKeyAction
import com.secdec.codepulse.data.model._

/** The Slick DAO for encounter data.
  *
  * @author robertf
  */
private[slick] class EncountersDao(val driver: JdbcProfile, val recordingMetadata: RecordingMetadataDao, val treeNodeData: TreeNodeDataDao, val sourceLocation: SourceDataDao) extends SlickHelpers {
	import driver.simple._

	class Encounters(tag: Tag) extends Table[Encounter](tag, "node_encounters") {
		def recordingId = column[Option[Int]]("recording_id", O.Nullable)
		def nodeId = column[Int]("node_id", O.NotNull)
		def sourceLocationId = column[Option[Int]]("source_location_id")
		def * = (recordingId, nodeId, sourceLocationId) <> (Encounter.tupled, Encounter.unapply)

		def recording = foreignKey("ne_recording", recordingId, recordingMetadata.recordings)(_.id, onDelete = ForeignKeyAction.Cascade)
		def node = foreignKey("ne_node", nodeId, treeNodeData.treeNodeData)(_.id, onDelete = ForeignKeyAction.Cascade)
		def sourceLocationKey = foreignKey("ne_sourcelocation", sourceLocationId, sourceLocation.sourceLocationsQuery)(_.id, onDelete = ForeignKeyAction.Cascade)
	}
	val encounters = TableQuery[Encounters]

	def create(implicit session: Session) = encounters.ddl.create

	def iterateWith[T](f: Iterator[Encounter] => T)(implicit session: Session): T = {
		val it = encounters.iterator
		try {
			f(it)
		} finally it.close
	}

	def store(entries: Iterable[(Option[Int], (Int, Option[Int]))])(implicit session: Session) {
		val entriesToStore = entries.map(x => Encounter(x._1, x._2._1, x._2._2))
		fastImport { encounters ++= entriesToStore }
	}

	def getTracedSourceLocations(nodeId: Int)(implicit session: Session): List[SourceLocation] = {
		(for {
			encounter <- encounters.filter(_.nodeId === nodeId)
			sourceLocation <- encounter.sourceLocationKey
		} yield (sourceLocation)).list()
	}
}
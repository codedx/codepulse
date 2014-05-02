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

/** The Slick DAO for recording metadata.
  *
  * @author robertf
  */
private[slick] class RecordingMetadataDao(val driver: JdbcProfile) {
	import driver.simple._

	class Recordings(tag: Tag) extends Table[Int](tag, "recordings") {
		def id = column[Int]("id", O.AutoInc)
		def * = id
	}
	val recordings = TableQuery[Recordings]

	class RecordingMetadata(tag: Tag) extends Table[(Int, String, String)](tag, "recording_metadata") {
		def recordingId = column[Int]("recording_id", O.NotNull)
		def key = column[String]("key", O.NotNull)
		def value = column[String]("value", O.NotNull)
		def pk = primaryKey("pk_recording_metadata", (recordingId, key))
		def * = (recordingId, key, value)

		def recording = foreignKey("rm_recording", recordingId, recordings)(_.id, onDelete = ForeignKeyAction.Cascade)
	}
	val recordingMetadata = TableQuery[RecordingMetadata]

	def create(implicit session: Session) = (recordings.ddl ++ recordingMetadata.ddl).create

	def createRecording()(implicit session: Session) = {
		val newId = (recordings returning recordings.map(_.id)) += -1
		newId
	}

	def deleteRecording(id: Int)(implicit session: Session) = {
		(for (r <- recordings if r.id === id) yield r).delete
	}

	def getRecordings()(implicit session: Session): List[Int] =
		(for (r <- recordings) yield r.id).list

	def getMap(recordingId: Int)(implicit session: Session): Map[String, String] =
		(for (r <- recordingMetadata if r.recordingId === recordingId) yield (r.key, r.value)).list.toMap

	def get(recordingId: Int, key: String)(implicit session: Session): Option[String] =
		(for (r <- recordingMetadata if r.recordingId === recordingId && r.key === key) yield r.value).firstOption

	def set(recordingId: Int, key: String, value: String)(implicit session: Session) {
		set(recordingId, key, Some(value))
	}

	def set(recordingId: Int, key: String, value: Option[String])(implicit session: Session) {
		value match {
			case Some(value) =>
				get(recordingId, key) match {
					case Some(_) => (for (r <- recordingMetadata if r.recordingId === recordingId && r.key === key) yield r.value).update(value)
					case None => recordingMetadata += (recordingId, key, value)
				}

			case None =>
				(for (r <- recordingMetadata if r.recordingId === recordingId && r.key === key) yield r).delete
		}
	}
}
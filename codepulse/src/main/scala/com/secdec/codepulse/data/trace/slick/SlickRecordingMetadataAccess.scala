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

package com.secdec.codepulse.data.trace.slick

import scala.slick.jdbc.JdbcBackend.{ Database, Session }
import com.secdec.codepulse.data.trace.{ RecordingMetadata, RecordingMetadataAccess }
import net.liftweb.util.Helpers.AsBoolean

/** Slick-backed RecordingMetadata implementation.
  *
  * @author robertf
  */
private[slick] class SlickRecordingMetadata(val id: Int, dao: RecordingMetadataDao, db: Database) extends RecordingMetadata {
	private val cache = collection.mutable.Map.empty[String, Option[String]]

	private def get(key: String)(implicit session: Session) = cache.getOrElseUpdate(key, dao.get(id, key))
	private def set(key: String, value: String)(implicit session: Session): Unit = set(key, Some(value))
	private def set(key: String, value: Option[String])(implicit session: Session) {
		dao.set(id, key, value)
		cache += key -> value
	}

	def running = db withSession { implicit session =>
		get("running").flatMap(AsBoolean.unapply) getOrElse false
	}

	def running_=(newState: Boolean) = db withTransaction { implicit transaction =>
		set("running", newState.toString)
		newState
	}

	def clientLabel = db withSession { implicit session =>
		get("clientLabel")
	}

	def clientLabel_=(newLabel: Option[String]) = db withTransaction { implicit transaction =>
		set("clientLabel", newLabel)
		newLabel
	}

	def clientColor = db withSession { implicit session =>
		get("clientColor")
	}

	def clientColor_=(newColor: Option[String]) = db withTransaction { implicit transaction =>
		set("clientColor", newColor)
		newColor
	}
}

/** Slick-backed RecordingMetadataAccess implementation.
  *
  * @author robertf
  */
private[slick] class SlickRecordingMetadataAccess(dao: RecordingMetadataDao, db: Database) extends RecordingMetadataAccess {
	private def metadataFor(id: Int) = new SlickRecordingMetadata(id, dao, db)

	private lazy val cache = {
		val recordings = collection.mutable.Map.empty[Int, RecordingMetadata]
		recordings ++= db withSession { dao.getRecordings()(_) } map { id => id -> metadataFor(id) }
		recordings
	}

	def all: List[RecordingMetadata] = cache.values.toList
	def contains(id: Int): Boolean = cache.contains(id)

	def create(): RecordingMetadata = db withTransaction { implicit transaction =>
		val newId = dao.createRecording
		get(newId)
	}

	def get(id: Int): RecordingMetadata = cache.getOrElseUpdate(id, metadataFor(id))

	def remove(id: Int) {
		db withTransaction { implicit transaction =>
			dao deleteRecording id
			cache -= id
		}
	}
}
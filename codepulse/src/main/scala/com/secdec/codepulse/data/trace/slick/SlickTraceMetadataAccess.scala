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
import com.secdec.codepulse.data.trace.{ DefaultTraceMetadataUpdates, TraceMetadataAccess, TraceMetadata }
import net.liftweb.util.Helpers.AsLong
import net.liftweb.util.Helpers.AsBoolean

/** Master controller for centralized Slick-backed metadata access.
  *
  * @author robertf
  */
private[slick] class SlickTraceMetadataMaster(dao: TraceMetadataDao, db: Database) {
	def get(traceId: Int) = new SlickTraceMetadataAccess(traceId, dao, db) with DefaultTraceMetadataUpdates with TraceMetadata
}

/** Slick-backed TraceMetadataAccess implementation.
  *
  * @author robertf
  */
private[slick] class SlickTraceMetadataAccess(traceId: Int, dao: TraceMetadataDao, db: Database) extends TraceMetadataAccess {
	private val cache = collection.mutable.Map.empty[String, Option[String]]

	private def get(key: String)(implicit session: Session) = cache.getOrElseUpdate(key, dao.get(traceId, key))
	private def set(key: String, value: String)(implicit session: Session): Unit = set(key, Some(value))
	private def set(key: String, value: Option[String])(implicit session: Session) {
		dao.set(traceId, key, value)
		cache += key -> value
	}

	def delete() {
		db withSession { implicit session =>
			dao delete traceId
		}
	}

	def name = db withSession { implicit session =>
		get("name")
	} getOrElse "Untitled"

	def name_=(newName: String) = db withTransaction { implicit transaction =>
		set("name", newName)
		set("hasCustomName", "true")
		newName
	}

	def hasCustomName = db withSession { implicit session =>
		get("hasCustomName") flatMap { AsBoolean.unapply }
	} getOrElse false

	def creationDate = db withSession { implicit session =>
		get("creationDate").flatMap(AsLong.unapply)
	} getOrElse System.currentTimeMillis

	def creationDate_=(newDate: Long) = db withTransaction { implicit transaction =>
		set("creationDate", newDate.toString)
		newDate
	}

	def importDate = db withSession { implicit session =>
		get("importDate").flatMap(AsLong.unapply)
	}

	def importDate_=(newDate: Option[Long]) = db withTransaction { implicit transaction =>
		set("importDate", newDate.map(_.toString))
		newDate
	}
}
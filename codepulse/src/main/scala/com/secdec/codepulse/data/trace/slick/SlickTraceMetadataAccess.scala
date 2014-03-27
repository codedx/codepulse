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

import scala.slick.jdbc.JdbcBackend.Database
import com.secdec.codepulse.data.trace.TraceMetadataAccess
import net.liftweb.util.Helpers.AsLong

/** Slick-backed TraceMetadataAccess implementation.
  *
  * @author robertf
  */
private[slick] class SlickTraceMetadataAccess(dao: TraceMetadataDao, db: Database) extends TraceMetadataAccess {
	def name = db withSession { implicit session =>
		dao get "name"
	} getOrElse "Untitled"

	def name_=(newName: String) = db withTransaction { implicit transaction =>
		dao.set("name", newName)
		newName
	}

	def creationDate = db withSession { implicit session =>
		dao.get("creationDate").flatMap(AsLong.unapply)
	} getOrElse System.currentTimeMillis

	def creationDate_=(newDate: Long) = db withTransaction { implicit transaction =>
		dao.set("creationDate", newDate.toString)
		newDate
	}

	def importDate = db withSession { implicit session =>
		dao.get("importDate").flatMap(AsLong.unapply)
	}

	def importDate_=(newDate: Option[Long]) = db withTransaction { implicit transaction =>
		dao.set("importDate", newDate.map(_.toString))
		newDate
	}
}
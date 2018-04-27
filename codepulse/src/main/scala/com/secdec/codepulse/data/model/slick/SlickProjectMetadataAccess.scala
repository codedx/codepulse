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

import scala.slick.jdbc.JdbcBackend.{ Database, Session }
import com.secdec.codepulse.data.model.{ DefaultProjectMetadataUpdates, ProjectMetadataAccess, ProjectMetadata }
import com.secdec.codepulse.dependencycheck.{ DependencyCheckStatus, TransientDependencyCheckStatus }
import net.liftweb.util.Helpers.AsLong
import net.liftweb.util.Helpers.AsBoolean

/** Master controller for centralized Slick-backed metadata access.
  *
  * @author robertf
  */
private[slick] class SlickProjectMetadataMaster(dao: ProjectMetadataDao, db: Database) {
	def get(projectId: Int) = new SlickProjectMetadataAccess(projectId, dao, db) with DefaultProjectMetadataUpdates with ProjectMetadata
}

/** Slick-backed ProjectMetadataAccess implementation.
  *
  * @author robertf
  */
private[slick] class SlickProjectMetadataAccess(projectId: Int, dao: ProjectMetadataDao, db: Database) extends ProjectMetadataAccess {
	private val cache = collection.mutable.Map.empty[String, Option[String]]

	private def get(key: String)(implicit session: Session) = cache.getOrElseUpdate(key, dao.get(projectId, key))
	private def set(key: String, value: String)(implicit session: Session): Unit = set(key, Some(value))
	private def set(key: String, value: Option[String])(implicit session: Session) {
		dao.set(projectId, key, value)
		cache += key -> value
	}

	def delete() {
//		db withSession { implicit session =>
//			dao delete projectId
//		}
		deleted = true
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

	def deleted =  db withSession { implicit session =>
		get("deleted") flatMap { AsBoolean.unapply }
	} getOrElse false

	def deleted_=(softDelete: Boolean) = db withTransaction { implicit transaction =>
		set("deleted", softDelete.toString)
		softDelete
	}

	def input = db withSession { implicit session =>
		get("input")
	} getOrElse ("")

	def input_=(newInput: String) = db withTransaction { implicit transaction =>
		set("input", newInput)
		newInput
	}

	private object DependencyCheckStatusHelpers {
		private val FinishedStatus = raw"finished\((\d+), (\d+)\)".r
		private def finishedStatus(numDeps: Int, numFlagged: Int) = s"finished($numDeps, $numFlagged)"
		private val FailedStatus = "failed"
		private val NotRunStatus = "notrun"
		private val UnknownStatus = "unknown"

		def serialize(status: DependencyCheckStatus): String = status match {
			case _: TransientDependencyCheckStatus => serialize(DependencyCheckStatus.Unknown)
			case DependencyCheckStatus.Finished(numDeps, numFlagged) => finishedStatus(numDeps, numFlagged)
			case DependencyCheckStatus.Failed => FailedStatus
			case DependencyCheckStatus.NotRun => NotRunStatus
			case DependencyCheckStatus.Unknown => UnknownStatus
		}

		def deserialize(status: String): Option[DependencyCheckStatus] = status match {
			case FinishedStatus(numDeps, numFlagged) => Some(DependencyCheckStatus.Finished(numDeps.toInt, numFlagged.toInt))
			case FailedStatus => Some(DependencyCheckStatus.Failed)
			case NotRunStatus => Some(DependencyCheckStatus.NotRun)
			case UnknownStatus => Some(DependencyCheckStatus.Unknown)
			case _ => None
		}

		private var transientStatus = None: Option[TransientDependencyCheckStatus]

		def dependencyCheckStatus(implicit session: Session) = {
			transientStatus getOrElse {
				get("dependencyCheckStatus").flatMap(deserialize) getOrElse DependencyCheckStatus.NotRun
			}
		}

		def dependencyCheckStatus_=(newStatus: DependencyCheckStatus)(implicit session: Session) = {
			set("dependencyCheckStatus", serialize(newStatus))
			newStatus match {
				case transient: TransientDependencyCheckStatus => transientStatus = Some(transient)
				case _ => transientStatus = None
			}
			newStatus
		}
	}

	def dependencyCheckStatus = db withSession { implicit session =>
		DependencyCheckStatusHelpers.dependencyCheckStatus
	}

	def dependencyCheckStatus_=(newStatus: DependencyCheckStatus) = db withTransaction { implicit transaction =>
		DependencyCheckStatusHelpers.dependencyCheckStatus = newStatus
	}
}
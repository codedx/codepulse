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

import java.io.File

import scala.concurrent.duration._
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.Database

import com.secdec.codepulse.data.model._
import com.secdec.codepulse.util.RichFile.RichFile

import akka.actor.ActorSystem

/** Provides `SlickProjectData` instances for projects, basing storage in `folder`.
  * Uses H2 for data storage.
  *
  * @author robertf
  */
class SlickH2ProjectDataProvider(folder: File, actorSystem: ActorSystem) extends ProjectDataProvider {
	private val EncountersBufferSize = 500
	private val EncountersFlushInterval = 1.second

	private val cache = collection.mutable.Map.empty[ProjectId, SlickProjectData]

	val MasterDbName = "master"
	val PageStoreFileSuffix = ".h2.db"
	val MultiVersionStoreFileSuffix = ".mv.db"

	private object ProjectFilename {
		def apply(folder: File, projectId: ProjectId) = {
			val dbName = getDbName(projectId)
			val dbFolder = getDbFolder(folder, projectId)

			// The current H2 database driver reads existing PageStore db files
			// but will create new db files using MVStore.
			val dbPageStoreFilename = s"$dbName$PageStoreFileSuffix"
			if ((dbFolder / dbPageStoreFilename).exists)
				dbPageStoreFilename
			else
				s"$dbName$MultiVersionStoreFileSuffix"
		}

		def getDbFolder(folder: File, projectId: ProjectId) = {
			folder / s"project-${projectId.num}"
		}

		def getDbName(projectId: ProjectId) = s"project-${projectId.num}"

		val NameRegex = raw"project-(\d+)\.(?:h2|mv)\.db".r

		def unapply(filename: String): Option[ProjectId] = filename match {
			case NameRegex(ProjectId(id)) => Some(id)
			case _ => None
		}
	}

	private val masterData = {
		val needsInit = !((folder / s"$MasterDbName$MultiVersionStoreFileSuffix").exists || (folder / s"$MasterDbName$PageStoreFileSuffix").exists)

		val db = Database.forURL(s"jdbc:h2:file:${(folder / MasterDbName).getCanonicalPath};DB_CLOSE_DELAY=10", driver = "org.h2.Driver")
		val data = new SlickMasterData(db, H2Driver)

		if (needsInit) data.init

		data
	}

	def getProject(id: ProjectId): ProjectData = getProjectInternal(id)

	private def getProjectInternal(id: ProjectId, suppressInit: Boolean = false) = cache.getOrElseUpdate(id, {
		val dbFolder = ProjectFilename.getDbFolder(folder, id)
		val needsInit = !(dbFolder / ProjectFilename(folder, id)).exists

		val db = Database.forURL(s"jdbc:h2:file:${(dbFolder / ProjectFilename.getDbName(id)).getCanonicalPath};DB_CLOSE_DELAY=10", driver = "org.h2.Driver")
		val data = new SlickProjectData(id, db, H2Driver, masterData.metadataMaster get id.num, EncountersBufferSize, EncountersFlushInterval, actorSystem)

		if (!suppressInit && needsInit) data.init

		data
	})

	def removeProject(id: ProjectId) {
		getProjectInternal(id, true).delete
		cache -= id
	}

	def projectList: List[ProjectId] = {
		var folders = folder.listFiles.filter(_.isDirectory).toList
		var files = folders.flatMap(_.listFiles)
		var names = files.map(_.getName)
		var projects = folder.listFiles.filter(_.isDirectory).toList.flatMap(_.listFiles).map { _.getName } collect {
			case ProjectFilename(id) => id
		} filter { id =>
			val project = getProject(id)
			// If this project has been soft-deleted, exclude
			!project.metadata.deleted
		}

		projects
	}

	def maxProjectId: Int = {
		val default = 0
		(folder.listFiles.filter(_.isDirectory).toList.flatMap(_.listFiles).map { _.getName } collect {
			case ProjectFilename(id) => id
		} map(id => id.num) foldLeft default)(Math.max)
	}
}
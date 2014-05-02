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

	private object ProjectFilename {
		def apply(projectId: ProjectId) = s"${getDbName(projectId)}.h2.db"
		def getDbName(projectId: ProjectId) = s"project-${projectId.num}"

		val NameRegex = raw"project-(\d+)\.h2\.db".r

		def unapply(filename: String): Option[ProjectId] = filename match {
			case NameRegex(ProjectId(id)) => Some(id)
			case _ => None
		}
	}

	private val masterData = {
		val needsInit = !(folder / "master.h2.db").exists

		val db = Database.forURL(s"jdbc:h2:file:${(folder / "master").getCanonicalPath};DB_CLOSE_DELAY=10", driver = "org.h2.Driver")
		val data = new SlickMasterData(db, H2Driver)

		if (needsInit) data.init

		data
	}

	def getProject(id: ProjectId): ProjectData = getProjectInternal(id)

	private def getProjectInternal(id: ProjectId, suppressInit: Boolean = false) = cache.getOrElseUpdate(id, {
		val needsInit = !(folder / ProjectFilename(id)).exists

		val db = Database.forURL(s"jdbc:h2:file:${(folder / ProjectFilename.getDbName(id)).getCanonicalPath};DB_CLOSE_DELAY=10", driver = "org.h2.Driver")
		val data = new SlickProjectData(db, H2Driver, masterData.metadataMaster get id.num, EncountersBufferSize, EncountersFlushInterval, actorSystem)

		if (!suppressInit && needsInit) data.init

		data
	})

	def removeProject(id: ProjectId) {
		getProjectInternal(id, true).delete
		cache -= id
	}

	def projectList: List[ProjectId] = {
		folder.listFiles.toList.map { _.getName } collect {
			case ProjectFilename(id) => id
		}
	}
}
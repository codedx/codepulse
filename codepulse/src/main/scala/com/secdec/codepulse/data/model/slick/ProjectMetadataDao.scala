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
import com.secdec.codepulse.data.model._

/** The Slick DAO for project metadata.
  *
  * @author robertf
  */
class ProjectMetadataDao(val driver: JdbcProfile) {
	import driver.simple._

	class ProjectMetadata(tag: Tag) extends Table[(Int, String, String)](tag, "project_metadata") {
		def projectId = column[Int]("project_id", O.NotNull)
		def key = column[String]("key", O.NotNull)
		def value = column[String]("value", O.NotNull)
		def pk = primaryKey("pk_project_metadata", (projectId, key))
		def * = (projectId, key, value)
	}
	val projectMetadata = TableQuery[ProjectMetadata]

	def create(implicit session: Session) = projectMetadata.ddl.create

	def getMap()(implicit session: Session): Map[Int, Map[String, String]] = projectMetadata.list.groupBy(_._1) mapValues {
		entries => (entries map { case (_, key, value) => key -> value }).toMap
	}

	def get(projectId: Int, key: String)(implicit session: Session): Option[String] =
		(for (r <- projectMetadata if r.projectId === projectId && r.key === key) yield r.value).firstOption

	def set(projectId: Int, key: String, value: String)(implicit session: Session) {
		set(projectId, key, Some(value))
	}

	def set(projectId: Int, key: String, value: Option[String])(implicit session: Session) {
		value match {
			case Some(value) =>
				get(projectId, key) match {
					case Some(_) => (for (r <- projectMetadata if r.projectId === projectId && r.key === key) yield r.value).update(value)
					case None => projectMetadata += (projectId, key, value)
				}

			case None =>
				(for (r <- projectMetadata if r.projectId === projectId && r.key === key) yield r).delete
		}
	}

	def delete(projectId: Int)(implicit session: Session) {
		(for (r <- projectMetadata if r.projectId === projectId) yield r).delete
	}
}
/*
 * Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */
package com.secdec.codepulse.data.model.slick

import scala.slick.jdbc.JdbcBackend.Database
import com.secdec.codepulse.data.model.{SourceDataAccess, SourceFile, SourceLocation}

private[slick] class SlickSourceDataAccess(dao: SourceDataDao, db: Database) extends SourceDataAccess {
	def importSourceFiles(sourceFileMap: Map[Int, String]) = {
		val sourceFiles = sourceFileMap.map { case (id, file) => SourceFile(id, file) }
		db withTransaction { implicit transaction => dao.storeSourceFiles(sourceFiles) }
	}

	def getSourceLocationId(sourceFileId: Int, startLine: Int, endLine: Int, startCharacter: Option[Int], endCharacter: Option[Int]): Int = {
		db withTransaction { implicit transaction => dao.getOrInsertSourceLocation(sourceFileId, startLine, endLine, startCharacter, endCharacter) }
	}

	def foreachSourceFile(f: SourceFile => Unit) {
		iterateSourceFile { _.foreach(f) }
	}

	def foreachSourceLocation(f: SourceLocation => Unit) {
		iterateSourceLocation { _.foreach(f) }
	}

	def iterateSourceFile[T](f: Iterator[SourceFile] => T): T = {
		db withSession { implicit session =>
			dao.iterateSourceFileWith(f)
		}
	}

	def iterateSourceLocation[T](f: Iterator[SourceLocation] => T): T = {
		db withSession { implicit session =>
			dao.iterateSourceLocationWith(f)
		}
	}
}

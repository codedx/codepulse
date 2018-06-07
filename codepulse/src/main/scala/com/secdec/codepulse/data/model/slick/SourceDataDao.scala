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

import scala.slick.driver.JdbcProfile
import scala.slick.model.ForeignKeyAction

import com.secdec.codepulse.data.model.{ SourceFile, SourceLocation }

private[slick] class SourceDataDao(val driver: JdbcProfile) extends SlickHelpers {
	import driver.simple._

	class SourceFileData(tag: Tag) extends Table[SourceFile](tag, "source_file") {
		def id = column[Int]("id", O.PrimaryKey, O.NotNull)
		def path = column[String]("path", O.NotNull)
		def * = (id, path) <> (SourceFile.tupled, SourceFile.unapply)
	}

	val sourceFilesQuery = TableQuery[SourceFileData]

	class SourceLocationData(tag: Tag) extends Table[SourceLocation](tag, "source_location") {
		def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
		def sourceFileId = column[Int]("source_file_id", O.NotNull)
		def startLine = column[Int]("start_line", O.NotNull)
		def endLine = column[Int]("end_line", O.NotNull)
		def startCharacter = column[Option[Int]]("start_character")
		def endCharacter = column[Option[Int]]("end_character")
		def * = (id, sourceFileId, startLine, endLine, startCharacter, endCharacter) <> (SourceLocation.tupled, SourceLocation.unapply)

		def sourceFile = foreignKey("source_location_to_source_file", sourceFileId, sourceFilesQuery)(_.id, onDelete = ForeignKeyAction.Cascade)
		def sourceLocationIndex = index("tnd_source_location_index", (sourceFileId, startLine, endLine, startCharacter, endCharacter), unique = true)
	}

	val sourceLocationsQuery = TableQuery[SourceLocationData]

	def create(implicit session: Session) = (sourceFilesQuery.ddl ++ sourceLocationsQuery.ddl).create

	def storeSourceFiles(sourceFiles: Iterable[SourceFile])(implicit session: Session) = {
		fastImport { sourceFilesQuery ++= sourceFiles }
	}

	def getOrInsertSourceLocation(sourceFileId: Int, startLine: Int, endLine: Int, startCharacter: Option[Int], endCharacter: Option[Int])(implicit session: Session): Int = {

		val sourceLocation = getSourceLocationId(sourceFileId, startLine, endLine, startCharacter, endCharacter)
		if (sourceLocation == None) {
			try {
				return sourceLocationsQuery returning sourceLocationsQuery.map(_.id) += SourceLocation(0, sourceFileId, startLine, endLine, startCharacter, endCharacter)
			} catch {
				case _: Throwable => {
					// assume race condition occurred
					return getSourceLocationId(sourceFileId, startLine, endLine, startCharacter, endCharacter).get
				}
			}
		}

		sourceLocation.get
	}

	def getSourceLocationId(sourceFileId: Int, startLine: Int, endLine: Int, startCharacter: Option[Int], endCharacter: Option[Int])(implicit session: Session): Option[Int] = {
		val sourceLocation = (for {x <- sourceLocationsQuery
															 if x.sourceFileId === sourceFileId &&
																 x.startLine === startLine &&
																 x.endLine === endLine &&
																 x.startCharacter === startCharacter &&
																 x.endCharacter === endCharacter}
			yield x).firstOption

		if (sourceLocation == None) { None } else { Option(sourceLocation.get.id) }
	}

	def getSourceFile(sourceFileId: Int)(implicit session: Session): Option[SourceFile] = {
		(for {
			q <- sourceFilesQuery if q.id === sourceFileId
		} yield q).firstOption
	}

	def iterateSourceFileWith[T](f: Iterator[SourceFile] => T)(implicit session: Session): T = {
		val it = sourceFilesQuery.iterator
		try {
			f(it)
		} finally it.close
	}

	def iterateSourceLocationWith[T](f: Iterator[SourceLocation] => T)(implicit session: Session): T = {
		val it = sourceLocationsQuery.iterator
		try {
			f(it)
		} finally it.close
	}
}

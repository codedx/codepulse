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
package com.secdec.codepulse.data.storage

import java.io.{ BufferedInputStream, FileInputStream, InputStream }
import java.util.zip.{ ZipEntry, ZipFile, ZipInputStream }
import collection.JavaConversions._
import scala.util.Try

import com.secdec.codepulse.input.pathnormalization.{ FilePath, NestedPath }
import org.apache.commons.io.input.CloseShieldInputStream
import com.secdec.codepulse.util.{ SmartLoader, ZipCleaner }
import com.secdec.codepulse.util.SmartLoader.Success

class ZippedStorage(zipFile: ZipFile) extends Storage {
	override def name = zipFile.getName

	override def close = zipFile.close()

	override def getEntries(filter: ZipEntry => Boolean): List[String] = {
		zipFile.entries.filter(filter).map(_.getName).toList
	}

	override def loadEntry(path: String): Option[String] = {
		val content = readEntry(path){ stream =>
			SmartLoader.loadStream(stream) match {
				case Success(content, _) => Some(content)
				case _ => None
			}
		}

		content
	}

	override def readEntry[T](path: String)(read: (InputStream) => Option[T]): Option[T] = {
		val nestedArchiveParts = path.split(';')

		if(nestedArchiveParts.length == 1) {
			readNonNestedEntry(path)(read)
		} else {
			val filePart = nestedArchiveParts.takeRight(1)
			val archiveParts = nestedArchiveParts.take(nestedArchiveParts.length - 1)

			// get initial stream
			val stream = new FileInputStream(zipFile.getName)

			// for each archive part, get stream pertaining to them
			var archiveStream: Option[InputStream] = Option(stream)
			archiveParts.foreach { p =>
				archiveStream match {
					case Some(s) => archiveStream = getStream(s, p)
					case None =>
				}
			}

			// get file part stream using last stream
			val fileStream = archiveStream.flatMap { s =>
				getStream(s, filePart.head)
			}

			// read file part stream
			fileStream match {
				case Some(s) => read(s)
				case None => None
			}
		}
	}

	private def readNonNestedEntry[T](path: String)(read: (InputStream) => Option[T]): Option[T] = {
		val entry = zipFile.getEntry(path)
		val stream = zipFile.getInputStream(entry)
		try {
			read(stream)
		} finally {
			stream.close
		}
	}

	private def getStream[T](stream: InputStream, path: String): Option[InputStream] = {
		val zipStream = new ZipInputStream(stream)
		try {
			val entryStream = Stream.continually(Try { zipStream.getNextEntry })
			    	.map(_.toOption.flatMap { Option(_) })
			        .takeWhile(_.isDefined)
			        .flatten

			val entry = entryStream.find(_.getName == path)
			entry match {
				case None => None
				case Some(e) => Option(new CloseShieldInputStream(zipStream))
			}
		} finally {
//			zipStream.close
		}
	}

	override def readEntries[T](recursive: Boolean = true)(read: (String, Option[NestedPath], ZipEntry, InputStream) => Unit) = {
		val stream = new BufferedInputStream(new FileInputStream(zipFile.getName))
		try {
			readEntries(_ => true, None, zipFile.getName, stream, recursive)(read)
		} finally {
			stream.close
		}
	}

	override def readEntries[T](filter: ZipEntry => Boolean, recursive: Boolean = true)(read: (String, Option[NestedPath], ZipEntry, InputStream) => Unit): Unit = {
		val stream = new BufferedInputStream(new FileInputStream(zipFile.getName))
		try {
			readEntries(filter, None, zipFile.getName, stream, recursive)(read)
		} finally {
			stream.close
		}
	}

	protected def readEntries[T](filter: ZipEntry => Boolean, entryPath: Option[NestedPath], filename: String, stream: InputStream, recursive: Boolean = true)(read: (String, Option[NestedPath], ZipEntry, InputStream) => Unit): Unit = {
		val zipStream = new ZipInputStream(stream)
		try {
			val entryStream = Stream.continually(Try {zipStream.getNextEntry})
				.map(_.toOption.flatMap {Option(_)})
				.takeWhile(_.isDefined)
				.flatten

			for {
				entry <- entryStream
				if !ZipCleaner.shouldFilter(entry)
			} {
				val nestedPath = entryPath match {
					case None =>
						FilePath(entry.getName) match {
							case Some(fp) => Option(new NestedPath(List(fp)))
							case None => None
						}
					case Some(ep) =>
						FilePath(entry.getName) match {
							case Some(fp) => Option(NestedPath(ep.paths :+ fp))
							case None => Option(ep)
						}
				}

				if (entry.isDirectory) {
					if (filter(entry)) read(filename, nestedPath, entry, zipStream) // caller may need to process directories, too
					if (recursive) readEntries(filter, nestedPath, s"$filename/${entry.getName}", new CloseShieldInputStream(zipStream), recursive)(read)
				}
				else if (Storage.isZip(entry.getName) && recursive)
					readEntries(filter, nestedPath, s"$filename/${entry.getName}", new CloseShieldInputStream(zipStream), recursive)(read)
				else if (filter(entry))
					read(filename, entryPath, entry, zipStream)
			}
		} catch {
			case ex: Exception => System.out.println(ex)
		} finally {
			zipStream.close
		}
	}

	override def find(recursive: Boolean = true)(predicate: (String, Option[NestedPath], ZipEntry, InputStream) => Boolean): Boolean = {
		val stream = new BufferedInputStream(new FileInputStream(zipFile.getName))

		try find(zipFile.getName, None, stream, recursive)(predicate) finally stream.close
	}

	protected def find(filename: String, entryPath: Option[NestedPath], stream: InputStream, recursive: Boolean)(predicate: (String, Option[NestedPath], ZipEntry, InputStream) => Boolean): Boolean = {
		val zipStream = new ZipInputStream(stream)
		try {
			val entryStream = Stream.continually(Try { zipStream.getNextEntry })
				.map(_.toOption.flatMap { Option(_) })
				.takeWhile(_.isDefined)
				.flatten
				.filterNot(ZipCleaner.shouldFilter)


			entryStream.exists { entry =>
				val nestedPath = entryPath match {
					case None =>
						FilePath(entry.getName) match {
							case Some(fp) => Option(new NestedPath(List(fp)))
							case None => None
						}
					case Some(ep) =>
						FilePath(entry.getName) match {
							case Some(fp) => Option(NestedPath(ep.paths :+ fp))
							case None => Option(ep)
						}
				}

				lazy val recurse = entry.isDirectory || (recursive &&
					Storage.isZip(entry.getName) &&
					find(s"$filename/${entry.getName}", nestedPath, new CloseShieldInputStream(zipStream), true)(predicate))

				predicate(filename, entryPath, entry, zipStream) || recurse
			}
		} finally {
			zipStream.close
		}
	}
}

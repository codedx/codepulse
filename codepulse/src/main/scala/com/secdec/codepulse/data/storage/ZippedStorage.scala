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
		val entry = zipFile.getEntry(path)
		val stream = zipFile.getInputStream(entry)
		try {
			read(stream)
		} finally {
			stream.close
		}
	}

	override def readEntries[T](recursive: Boolean = true)(read: (String, ZipEntry, InputStream) => Unit) = {
		val stream = new BufferedInputStream(new FileInputStream(zipFile.getName))
		try {
			readEntries(_ => true, zipFile.getName, stream, recursive)(read)
		} finally {
			stream.close
		}
	}

	override def readEntries[T](filter: ZipEntry => Boolean, recursive: Boolean = true)(read: (String, ZipEntry, InputStream) => Unit): Unit = {
		val stream = new BufferedInputStream(new FileInputStream(zipFile.getName))
		try {
			readEntries(filter, zipFile.getName, stream, recursive)(read)
		} finally {
			stream.close
		}
	}

	protected def readEntries[T](filter: ZipEntry => Boolean, filename: String, stream: InputStream, recursive: Boolean = true)(read: (String, ZipEntry, InputStream) => Unit): Unit = {
		val zipStream = new ZipInputStream(stream)
		try {
			val entryStream = Stream.continually(Try { zipStream.getNextEntry })
				.map(_.toOption.flatMap { Option(_) })
				.takeWhile(_.isDefined)
				.flatten

			for {
				entry <- entryStream
				if !ZipCleaner.shouldFilter(entry)
			} {
				if (entry.isDirectory || (Storage.isZip(entry.getName) && recursive))
					readEntries(filter, s"$filename/${entry.getName}", new CloseShieldInputStream(zipStream), true)(read)
				else if(filter(entry))
					read(filename, entry, zipStream)
			}
		} finally {
			zipStream.close
		}
	}

	override def find(recursive: Boolean = true)(predicate: (String, ZipEntry, InputStream) => Boolean): Boolean = {
		val stream = new BufferedInputStream(new FileInputStream(zipFile.getName))

		try find(zipFile.getName, stream, recursive)(predicate) finally stream.close
	}

	protected def find(filename: String, stream: InputStream, recursive: Boolean)(predicate: (String, ZipEntry, InputStream) => Boolean): Boolean = {
		val zipStream = new ZipInputStream(stream)
		try {
			val entryStream = Stream.continually(Try { zipStream.getNextEntry })
				.map(_.toOption.flatMap { Option(_) })
				.takeWhile(_.isDefined)
				.flatten
				.filterNot(ZipCleaner.shouldFilter)

			entryStream.exists { entry =>
				lazy val recurse = entry.isDirectory || (recursive &&
					Storage.isZip(entry.getName) &&
					find(s"$filename/${entry.getName}", new CloseShieldInputStream(zipStream), true)(predicate))

				predicate(filename, entry, zipStream) || recurse
			}
		} finally {
			zipStream.close
		}
	}
}

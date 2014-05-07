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

package com.secdec.codepulse.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

import scala.collection.JavaConversions._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.input.CloseShieldInputStream

object ZipEntryChecker extends ZipEntryChecker

trait ZipEntryChecker {
	import collection.JavaConversions._

	def checkForZipEntries(file: File)(predicate: ZipEntry => Boolean): Boolean = {
		Try { new ZipFile(file) } match {
			// non-zip files don't work in this checker
			case Failure(_) => false

			// check the zipfile for matching entries, then close it
			case Success(zipfile) => {
				try {
					// check if find(predicate) finds anything
					zipfile.entries.filterNot(ZipCleaner.shouldFilter).find(predicate).isDefined
				} catch {
					// if something went wrong and uncaught, then the check fails
					case e: Throwable => false
				} finally {
					zipfile.close
				}
			}
		}
	}

	def isZip(name: String): Boolean = FilenameUtils.getExtension(name) match {
		case "zip" | "jar" | "war" => true
		case _ => false
	}

	def forEachEntry(filename: String, stream: InputStream, recursive: Boolean)(callback: (String, ZipEntry, InputStream) => Unit) {
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
				if (isZip(entry.getName) && recursive)
					forEachEntry(s"$filename/${entry.getName}", new CloseShieldInputStream(zipStream), true)(callback)
				else
					callback(filename, entry, zipStream)
			}
		} finally {
			zipStream.close
		}
	}

	def forEachEntry(file: File, recursive: Boolean = true)(callback: (String, ZipEntry, InputStream) => Unit) {
		val stream = new BufferedInputStream(new FileInputStream(file))
		try {
			forEachEntry(file.getName, stream, recursive)(callback)
		} finally {
			stream.close
		}
	}
}
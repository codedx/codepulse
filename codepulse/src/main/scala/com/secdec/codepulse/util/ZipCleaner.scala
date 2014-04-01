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

import java.io.File
import java.io.OutputStream
import java.util.zip._

import scala.collection.JavaConversions._
import scala.util.Success
import scala.util.Try

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

/** A helper to clean up sludge in zip files as they're being copied into the StorageManager.
  *
  * The primary purpose for this is to remove the __MACOSX folder and .DS_Store files that
  * OS X insists on creating/adding. FindBugs, in particular, has issues with processing when
  * the __MACOSX folder exists.
  *
  * @author robertf
  */
object ZipCleaner {
	// filter out __MACOSX from the root, and .svn folders / .DS_Store files all over
	private val Filter = """^__MACOSX(?:/.*)?$|(?:^|.*/)\.svn(?:/.*)?$|(?:^|.*/)\.DS_Store$""".r

	private def needsFiltering(file: ZipFile) = file.entries.exists(shouldFilter)

	/** Checks whether or not a given zip entry should be filtered */
	def shouldFilter(e: ZipEntry) = Filter.pattern.matcher(e.getName).matches

	/** Filters a zip, from a file to an output stream. Zips that do not contain offending files
	  * are left unaltered, as are files that aren't zips.
	  */
	def cleanZip(source: File)(out: OutputStream) {
		val zip = Try(new ZipFile(source))

		zip match {
			case Success(zip) if needsFiltering(zip) =>
				// filtering is necessary, so build a new zip on the output stream
				val zout = new ZipOutputStream(out)
				try {
					for (entry <- zip.entries.filterNot(shouldFilter)) {
						zout.putNextEntry(new ZipEntry(entry.getName))

						val in = zip.getInputStream(entry)
						try {
							IOUtils.copyLarge(in, zout)
						} finally {
							in.close
						}
					}
				} finally {
					zout.close
				}

			case _ =>
				// not a zip, or doesn't need filtering. just copy it.
				FileUtils.copyFile(source, out)
		}

		zip.foreach(_.close)
	}
}
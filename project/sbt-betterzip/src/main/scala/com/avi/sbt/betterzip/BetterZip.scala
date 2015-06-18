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

package com.avi.sbt.betterzip

import sbt.ErrorHandling.translate
import sbt.Using._

import scala.annotation.tailrec
import scala.language.implicitConversions

import java.io.File

import org.apache.commons.compress.archivers.zip.{ AsiExtraField, ZipArchiveEntry, ZipArchiveOutputStream }
import org.apache.commons.io.FileUtils

/** A better IO.zip implementation supporting file permissions (using commons-compress).
  *
  * @author robertf
  */
object BetterZip {
	val ExecutableMode = 493 // octal 755: r/w/x owner, r/x group, r/x all

	// asiMode seems to work on mac, whereas unixmode on linux. the two seem to be mutually exclusive, though
	sealed trait FileMode
	case class AsiExtraFieldMode(mode: Int) extends FileMode
	case class UnixMode(mode: Int) extends FileMode

	sealed trait ExecutableType { def mode: Option[FileMode] }
	object ExecutableType {
		case object Windows extends ExecutableType { val mode = None }
		case object Mac extends ExecutableType { val mode = Some(AsiExtraFieldMode(ExecutableMode)) }
		case object Unix extends ExecutableType { val mode = Some(UnixMode(ExecutableMode)) }
	}

	case class Entry(source: File, destination: String, mode: Option[FileMode]) {
		private[BetterZip] lazy val normalized = Entry(source, normalizeName(destination), mode)
	}

	implicit class EntryGenerator(source: File) {
		def ->(destination: String) = Entry(source, destination, None)
		def ->*(destination: String, executableType: ExecutableType) = Entry(source, destination, executableType.mode)
	}

	implicit def mappingToEntry(mapping: (File, String)) = Entry(mapping._1, mapping._2, None)
	implicit def mappingSeqToEntry(mappings: Seq[(File, String)]) = mappings map mappingToEntry

	def zip(sources: Traversable[Entry], outputZip: File) {
		if (outputZip.isDirectory)
			sys.error("Specified output file " + outputZip + " is a directory.")
		else withZipOutput(outputZip) { zout =>
			for (file <- sources.filter(_.source.isFile).map(_.normalized)) {
				val entry = new ZipArchiveEntry(file.source, file.destination)
				for (mode <- file.mode) mode match {
					case AsiExtraFieldMode(mode) =>
						val aef = new AsiExtraField
						aef setMode mode
						entry addExtraField aef

					case UnixMode(mode) =>
						entry setUnixMode mode
				}

				zout putArchiveEntry entry
				FileUtils.copyFile(file.source, zout)
				zout.closeArchiveEntry
			}
		}
	}

	private def withZipOutput(file: File)(thunk: ZipArchiveOutputStream => Unit) {
		val stream = new ZipArchiveOutputStream(file)
		try { thunk(stream) }
		finally { stream.close }
	}

	private def normalizeName(name: String) = File.separatorChar match {
		case '/' => name
		case sep => name.replace(sep, '/')
	}
}
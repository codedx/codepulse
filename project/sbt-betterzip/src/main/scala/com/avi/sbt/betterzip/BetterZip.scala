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

/** A better IO.zip implementation supporting file permissions (using commons-compress).
  *
  * @author robertf
  */
object BetterZip {
	private val BufferSize = 8192

	case class Entry(source: File, destination: String, fileMode: Option[Int]) {
		private[BetterZip] lazy val normalized = Entry(source, normalizeName(destination), fileMode)
	}

	class EntryGenerator(source: File) {
		def ->(destination: String) = Entry(source, destination, None)
		def ->*(destination: String) = Entry(source, destination, Some(755)) // r/w/x owner, r/x group, r/x all
	}

	implicit def entryGenerator(source: File) = new EntryGenerator(source)
	implicit def mappingToEntry(mapping: (File, String)) = Entry(mapping._1, mapping._2, None)
	implicit def mappingSeqToEntry(mappings: Seq[(File, String)]) = mappings map mappingToEntry

	def zip(sources: Traversable[Entry], outputZip: File) {
		if (outputZip.isDirectory)
			sys.error("Specified output file " + outputZip + " is a directory.")
		else withZipOutput(outputZip) { zout =>
			for (file <- sources.filter(_.source.isFile).map(_.normalized)) {
				val entry = new ZipArchiveEntry(file.source, file.destination)
				for (mode <- file.fileMode) {
					val aef = new AsiExtraField
					aef setMode mode
					entry addExtraField aef
				}

				zout putArchiveEntry entry
				transfer(file.source, zout)
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

	private def transfer(in: File, out: ZipArchiveOutputStream) {
		fileInputStream(in) { in =>
			val buffer = new Array[Byte](BufferSize)

			@tailrec
			def work() {
				val byteCount = in.read(buffer)
				if (byteCount >= 0) {
					out.write(buffer, 0, byteCount)
					work
				}
			}

			work
		}
	}
}
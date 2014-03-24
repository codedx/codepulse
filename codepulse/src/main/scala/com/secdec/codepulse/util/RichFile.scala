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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import scala.util.control.Exception._
import java.io.IOException
import java.io.BufferedInputStream
import scala.util.Try

object RichFile {
	/** Extended File class.
	  * @author dylanh
	  */
	implicit class RichFile(file: File) {

		def /(path: String) = new File(file, path)

		def isChildOf(dir: File): Boolean = {
			val absFile = file.getAbsoluteFile
			val absDir = dir.getAbsoluteFile

			def search(from: File): Boolean = {
				if (from == null) false
				else if (from == absDir) true
				else search(from.getParentFile)
			}

			search(absFile)
		}

		/** @return a list of this file's children. Will be `Nil` if this file isn't a directory */
		def children: List[File] = file.listFiles match {
			case null => Nil
			case array => array.toList
		}

		/** @return this file's parent, optionally */
		def parent: Option[File] = Option(file.getParentFile)

		def pathSegments: List[String] = {
			def path(f: File): List[String] = new RichFile(f).parent match {
				case Some(p) => f.getName :: path(p)
				case None => f.getName :: Nil
			}
			path(file).reverse
		}

		def read[A](body: InputStream => A): A = {
			val in = new FileInputStream(file)
			try {
				body(in)
			} finally {
				in.close
			}
		}

		def readBuffered[A](body: BufferedInputStream => A): A = read { in =>
			val buffered = new BufferedInputStream(in)
			try {
				body(buffered)
			} finally {
				buffered.close
			}
		}

		/** Write to this file.
		  * @param body A function that takes an output stream and writes to it.
		  * @return `true` if everything completes normally, `false` otherwise.
		  */
		def write(body: OutputStream => Unit): Boolean = {
			if (file.exists && !file.canWrite) false
			else {
				val out = new FileOutputStream(file)
				try {
					body(out)
					true
				} finally {
					out.close
				}
			}
		}

		def writeBuffered(body: OutputStream => Unit): Boolean = {
			write { out =>
				val buf = new BufferedOutputStream(out)
				try {
					body(buf)
				} finally {
					buf.close
				}
			}
		}

		/** Write the contents of the `stream` into this file.
		  * @param stream the source
		  * @return `true` on success, `false` on an error
		  */
		def loadFrom(stream: InputStream) = write { out =>
			try {
				val buf = new Array[Byte](2048);
				val itr = Iterator.continually {
					val count = stream.read(buf, 0, 2048)
					(buf, count)
				}.takeWhile(_._2 != -1)
				for { (buf, count) <- itr } out.write(buf, 0, count)
			} finally {
				stream.close
			}
		}
	}
}
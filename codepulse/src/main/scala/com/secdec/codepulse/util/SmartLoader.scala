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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

import scala.io.Codec
import scala.io.Codec.string2codec
import scala.io.Source

import org.mozilla.universalchardet.UniversalDetector

/** Loader that detects the proper character set when loading the contents of a
  * stream. Uses juniversalchardet.
  *
  * @author robertf
  */
class SmartLoader {
	private val BufferSize = 4096

	val detector = new UniversalDetector(null)

	/** Detect the character set used for `stream`.
	  * *NOTE*: this consumes the stream.
	  */
	def detectCharset(stream: InputStream): String = {
		detector.reset

		val buffer = new Array[Byte](BufferSize)

		val chunks = Iterator.continually {
			stream.read(buffer, 0, buffer.length) -> buffer
		} takeWhile { _._1 > 0 }

		for ((len, buffer) <- chunks.takeWhile(_ => !detector.isDone)) {
			detector.handleData(buffer, 0, len)
		}

		detector.dataEnd
		detector.getDetectedCharset
	}

	/** Detect charset for `stream` and decode all contents of the stream,
	  * returning a string. This buffers the entire contents of the stream in
	  * memory.
	  */
	def loadStream(stream: InputStream): String = {
		val bytes = {
			val bos = new ByteArrayOutputStream
			val buffer = new Array[Byte](BufferSize)

			val chunks = Iterator.continually {
				stream.read(buffer, 0, buffer.length) -> buffer
			} takeWhile { _._1 > 0 }

			for ((len, buffer) <- chunks) {
				bos.write(buffer, 0, len)
			}

			bos.flush
			bos.toByteArray
		}

		val charset = Option(detectCharset(new ByteArrayInputStream(bytes))).map[Codec](identity)
		Source.fromInputStream(new ByteArrayInputStream(bytes))(charset getOrElse Codec.UTF8).mkString
	}
}
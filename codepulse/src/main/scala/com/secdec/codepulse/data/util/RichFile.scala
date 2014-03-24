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

package com.secdec.codepulse.data.util

import java.io.File
import java.io.FileInputStream
import java.io.BufferedInputStream
import java.io.FileReader
import java.io.BufferedReader

object RichFile {
	import language.implicitConversions
	implicit def mkRichFile(file: File) = new RichFile(file)
}

case class RichFile(file: File) extends AnyVal {

	def stream[T](f: FileInputStream => T): T = {
		val stream = new FileInputStream(file)
		try {
			f(stream)
		} finally {
			stream.close()
		}
	}

	def streamBuffered[T](f: BufferedInputStream => T) = {
		val stream = new BufferedInputStream(new FileInputStream(file))
		try {
			f(stream)
		} finally {
			stream.close()
		}
	}

	def read[T](f: FileReader => T): T = {
		val reader = new FileReader(file)
		try {
			f(reader)
		} finally {
			reader.close()
		}
	}

	def readBuffered[T](f: BufferedReader => T): T = {
		val reader = new BufferedReader(new FileReader(file))
		try {
			f(reader)
		} finally {
			reader.close()
		}
	}
}
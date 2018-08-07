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

import java.io.{ File, InputStream }
import java.util.zip.{ ZipEntry, ZipFile }

import com.secdec.codepulse.input.pathnormalization.NestedPath
import org.apache.commons.io.FilenameUtils

trait Storage {
	def name: String

	def close

	def getEntries(filter: ZipEntry => Boolean): List[String]

	def loadEntry(path: String): Option[String]

	def readEntry[T](path: String)(read: InputStream => Option[T]): Option[T]

	def readEntries[T](recursive: Boolean = true)(read: (String, Option[NestedPath], ZipEntry, InputStream) => Unit): Unit

	def readEntries[T](filter: ZipEntry => Boolean, recursive: Boolean = true)(read: (String, Option[NestedPath], ZipEntry, InputStream) => Unit): Unit

	def find(recursive: Boolean = true)(predicate: (String, Option[NestedPath], ZipEntry, InputStream) => Boolean): Boolean
}

object Storage {
	def apply(file: File): Option[Storage] = {
		if(file.canRead && isZip(file.getName)) {
			Option(new ZippedStorage(new ZipFile(file)))
		} else {
			None
		}
	}

	def isZip(name: String): Boolean = FilenameUtils.getExtension(name) match {
		case "zip" | "ear" | "jar" | "war" => true
		case _ => false
	}
}
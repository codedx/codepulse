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

package com.secdec.codepulse.tracer

import scala.util.Random
import java.io.File
import java.io.BufferedInputStream
import com.secdec.codepulse.util.RichFile._
import org.apache.commons.io.IOUtils
import com.secdec.codepulse.data.trace.TraceId

class TraceDataSaveManager(saveDir: File) {

	// generate a random alphanumeric name with a given number of characters

	protected object TraceFilename {
		def apply(traceId: TraceId) = s"trace-${traceId.num}-data.zip"

		val NameRegex = raw"trace-(\d+)-data.zip".r

		def unapply(filename: String): Option[TraceId] = filename match {
			case NameRegex(TraceId(id)) => Some(id)
			case _ => None
		}
	}

	/** Save the given `data` to a file with the given `name`.
	  * If a file exists with that name, it will be replaced,
	  * but it will first be renamed so that data isn't accidentally
	  * lost. The new file will be written to a file with a "~0" suffix,
	  * and the old file will be renamed with a "~1" suffix. Once the
	  * new file has been completely (successfully) written, the old file
	  * will be deleted, and the new file will be renamed to the
	  * requested `name`.
	  */
	def save(id: TraceId, data: TraceData) = {
		val filename = TraceFilename(id)

		val oldFile = new File(saveDir, filename)
		val newFile = new File(saveDir, filename + "~0")
		val oldRep = new File(saveDir, filename + "~1")
		val oldExists = oldFile.exists

		if (oldExists) {
			oldFile.renameTo(oldRep)
		}

		TraceDataSerialization.traceDataToZip(data, newFile)

		newFile.renameTo(oldFile)

		if (oldExists) {
			oldRep.delete()
		}
	}

	def load(id: TraceId): Option[TraceData] = {
		val filename = TraceFilename(id)
		val file = new File(saveDir, filename)
		TraceDataSerialization.traceDataFromZip(file)
	}

	def loadRaw[T](id: TraceId): Array[Byte] = {
		val filename = TraceFilename(id)
		val file = new File(saveDir, filename)
		file.streamBuffered { stream =>
			IOUtils.toByteArray(stream)
		}
	}

	def delete(id: TraceId): Unit = {
		val filename = TraceFilename(id)
		val file = new File(saveDir, filename)
		if (file.exists) file.delete
	}

	def listStoredIds: List[TraceId] = {
		saveDir.listFiles.toList.map { _.getName } collect {
			case TraceFilename(id) => id
		}
	}

}
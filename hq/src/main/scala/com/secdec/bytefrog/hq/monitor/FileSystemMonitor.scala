/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.codedx.codepulse.hq.monitor

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.FileSystemException

case class FileSystemMonitorData(totalSpace: Long, freeSpace: Long) extends TraceComponentMonitorData

class FileSystemMonitor(dumpFile: File, minRemainingBytes: Long) extends HealthMonitor {

	private implicit val component = FilesystemComponent

	def checkHealth = {
		try {
			val fs = Files.getFileStore(FileSystems.getDefault.getPath(dumpFile.getCanonicalPath))
			val freeSpace = fs.getUsableSpace
			val totalSpace = fs.getTotalSpace

			val data = FileSystemMonitorData(totalSpace, freeSpace)
			if (freeSpace < minRemainingBytes) {
				concerned("Free space in the filesystem is running low.", data)
			} else {
				healthy(data)
			}
		} catch {
			case _: FileSystemException => concerned("Storage space unknown")
		}
	}

}
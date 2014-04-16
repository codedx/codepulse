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

package com.secdec.codepulse.data.trace

/** Helper utility for mass-importing tree node data into the data model.
  *
  * @author robertf
  */
class TreeNodeImporter(treeNodeData: TreeNodeDataAccess, bufferSize: Int) {
	import treeNodeData.{ BulkImportElement => BufferRecord }

	private val buffer = collection.mutable.ListBuffer.empty[BufferRecord]

	private def checkAndFlush() {
		if (buffer.size >= bufferSize)
			flush
	}

	def flush() {
		treeNodeData.bulkImport(buffer)
		buffer.clear
	}

	def +=(data: TreeNodeData) { +=(data, None) }
	def +=(data: TreeNodeData, traced: Boolean) { +=(data, Some(traced)) }
	def +=(data: TreeNodeData, traced: Option[Boolean]) {
		buffer += BufferRecord(data, traced)
		checkAndFlush
	}

	def ++=(data: Iterable[(TreeNodeData, Option[Boolean])]) {
		for (chunk <- data.grouped(bufferSize)) {
			treeNodeData.bulkImport(chunk map { case (data, traced) => BufferRecord(data, traced) })
		}
	}
}

object TreeNodeImporter {
	def apply(treeNodeData: TreeNodeDataAccess, bufferSize: Int = 2500) = new TreeNodeImporter(treeNodeData, bufferSize)
}
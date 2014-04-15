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

import java.io.{ OutputStream, OutputStreamWriter }

import com.secdec.codepulse.data.trace._

import com.fasterxml.jackson.core.{ JsonFactory, JsonGenerator }
import net.liftweb.http.OutputStreamResponse
import net.liftweb.json.Printer

/** Generates treemap JSON data in a streaming fashion.
  *
  * @author robertf
  */
object TreemapDataStreamer {
	private val Json = new JsonFactory

	private def writeJson(treeNodeData: TreeNodeDataAccess, jg: JsonGenerator)(node: TreeNode) {
		import treeNodeData.ExtendedTreeNodeData

		jg.writeStartObject

		jg.writeNumberField("id", node.data.id)
		for (parentId <- node.data.parentId) jg.writeNumberField("parentId", parentId)
		jg.writeStringField("name", node.data.label)
		jg.writeStringField("kind", node.data.kind.label)
		for (size <- node.data.size) jg.writeNumberField("lineCount", size)
		for (traced <- node.data.traced) jg.writeBooleanField("traced", traced)

		if (!node.children.isEmpty) {
			jg writeArrayFieldStart "children"
			node.children.foreach(writeJson(treeNodeData, jg))
			jg.writeEndArray
		}

		jg.writeEndObject
	}

	def streamTreemapData(treeNodeData: TreeNodeDataAccess, tree: List[TreeNode]): OutputStreamResponse = {
		def writeData(out: OutputStream) {
			val jg = Json createGenerator out

			try {
				jg.writeStartArray
				tree.foreach(writeJson(treeNodeData, jg))
				jg.writeEndArray
			} finally jg.close
		}

		OutputStreamResponse(writeData, -1L, List("Content-Type" -> "application/json; charset=utf-8"), Nil, 200)
	}
}
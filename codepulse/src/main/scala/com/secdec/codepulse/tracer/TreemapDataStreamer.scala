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

import net.liftweb.http.OutputStreamResponse
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer

/** Generates treemap JSON data in a streaming fashion.
  *
  * @author robertf
  */
object TreemapDataStreamer {
	private def toJsonField(node: TreeNode): JField = {
		val fields = List(
			node.parentId map { JField("parentId", _) },
			Some { JField("name", node.label) },
			Some { JField("kind", node.kind.label) },
			node.size map { JField("lineCount", _) }).flatten
		JField(node.id.toString, JObject(fields))
	}

	def streamTreemapData(traceData: TraceData): OutputStreamResponse = {
		def writeData(out: OutputStream) {
			val writer = new OutputStreamWriter(out)
			var first = true
			try {
				writer.write("{")
				traceData.treeNodeData.foreach { node =>
					if (!first) writer.write(",") else first = false
					val nodeField = toJsonField(node)
					Printer.compact(render(nodeField), writer)
				}
				writer.write("}")
			} finally writer.close
		}

		OutputStreamResponse(writeData, -1L, List("Content-Type" -> "application/json; charset=utf-8"), Nil, 200)
	}
}
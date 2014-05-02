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

import com.secdec.codepulse.data.model._

import com.fasterxml.jackson.core.{ JsonFactory, JsonGenerator }
import net.liftweb.http.OutputStreamResponse
import net.liftweb.json.Printer

/** Streams package tree data using jackson.
  *
  * @author robertf
  */
object PackageTreeStreamer {
	private val Json = new JsonFactory

	private def writeJson(jg: JsonGenerator)(node: PackageTreeNode) {
		jg.writeStartObject

		for (id <- node.id) jg.writeNumberField("id", id)
		jg.writeStringField("kind", node.kind.label)
		jg.writeStringField("label", node.label)
		jg.writeNumberField("methodCount", node.methodCount)
		for (traced <- node.traced) jg.writeBooleanField("traced", traced)

		if (!node.otherDescendantIds.isEmpty) {
			jg writeArrayFieldStart "otherDescendantIds"
			node.otherDescendantIds foreach { jg.writeNumber(_) }
			jg.writeEndArray
		}

		if (!node.children.isEmpty) {
			jg writeArrayFieldStart "children"
			node.children.foreach(writeJson(jg))
			jg.writeEndArray
		}

		jg.writeEndObject
	}

	def streamPackageTree(packageData: List[PackageTreeNode]): OutputStreamResponse = {
		def writeData(out: OutputStream) {
			val jg = Json createGenerator out

			try {
				jg.writeStartArray
				packageData.foreach(writeJson(jg))
				jg.writeEndArray
			} finally jg.close
		}

		OutputStreamResponse(writeData, -1L, List("Content-Type" -> "application/json; charset=utf-8"), Nil, 200)
	}
}
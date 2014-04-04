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

import java.io.File
import com.secdec.codepulse.data.bytecode.AsmVisitors
import com.secdec.codepulse.data.bytecode.CodeForestBuilder
import com.secdec.codepulse.data.trace.TraceId
import com.secdec.codepulse.tracer.export.TraceImporter
import com.secdec.codepulse.util.RichFile.RichFile
import com.secdec.codepulse.util.ZipEntryChecker
import net.liftweb.common.Box
import net.liftweb.common.Failure
import org.apache.commons.io.FilenameUtils
import com.secdec.codepulse.data.jsp.JasperJspAdapter

object TraceUploadData {

	def handleTraceExport(file: File): Box[TraceId] = {
		if (TraceImporter.canImportFrom(file)) {
			val traceId = traceManager.createTrace
			val traceData = traceDataProvider getTrace traceId

			try {
				TraceImporter.importFrom(file, traceData)

				// Note: the `creationDate` for trace data detected in this manner
				// should already be filled in with a non-default value. On the
				// other hand, the `importDate` is now.
				traceData.metadata.importDate = Some(System.currentTimeMillis)

				Some(traceId)
			} catch {
				case err: Exception =>
					println(s"Error importing file: $err")
					err.printStackTrace
					traceManager.removeTrace(traceId)
					None
			}
		} else None
	}

	def handleBinaryZip(file: File): Box[TraceId] = {
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = List.newBuilder[(String, Int)]

		//TODO: make this configurable somehow
		val jspAdapter = JasperJspAdapter.default

		ZipEntryChecker.forEachEntry(file) { (entry, contents) =>
			if (!entry.isDirectory) {
				FilenameUtils.getExtension(entry.getName) match {
					case "class" =>
						val methods = AsmVisitors.parseMethodsFromClass(contents)
						for {
							(name, size) <- methods
							treeNode <- builder.getOrAddMethod(name, size)
						} methodCorrelationsBuilder += (name -> treeNode.id)

					case "jsp" =>
						jspAdapter addJsp entry.getName

					case _ => // nothing
				}
			} else if (entry.getName.endsWith("WEB-INF/")) {
				jspAdapter addWebinf entry.getName
			}
		}

		val jspCorrelations = jspAdapter build builder
		val treemapNodes = builder.condensePathNodes().result
		val methodCorrelations = methodCorrelationsBuilder.result

		if (treemapNodes.isEmpty) None else Some {
			val traceId = traceManager.createTrace
			val traceData = traceDataProvider getTrace traceId

			// Note: the `creationDate` for trace data detected in this manner
			// should use its default value ('now'), as this is when the data
			// was actually 'created'.

			traceData.treeNodeData.storeNodes(treemapNodes)
			traceData.treeNodeData.mapJsps(jspCorrelations)
			traceData.treeNodeData.mapMethodSignatures(methodCorrelations)

			traceId
		}
	}

	def handleUpload(file: File): Box[TraceId] = {
		handleTraceExport(file) or handleBinaryZip(file) or Failure("Invalid upload file")
	}

}


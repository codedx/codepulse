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
import com.secdec.codepulse.util.ZipEntryChecker
import net.liftweb.common.Box
import net.liftweb.common.Box.option2Box
import net.liftweb.common.Failure

object TraceUploadData {

	//	def detectTraceExport(file: File): Box[TraceData] = {
	//		try {
	//			var result = TraceDataSerialization.traceDataFromZip(file)
	//
	//			// Note: the `creationDate` for trace data detected in this manner
	//			// should already be filled in with a non-default value. On the
	//			// other hand, the `importDate` is now.
	//			for (d <- result) d.importDate = Some(System.currentTimeMillis)
	//
	//			result
	//
	//		} catch {
	//			case err: Exception =>
	//				println(s"$file clearly isn't a zip...")
	//				None
	//		}
	//	}

	def handleBinaryZip(file: File): Box[TraceId] = {
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = List.newBuilder[(String, Int)]
		ZipEntryChecker.forEachEntry(file) { (entry, contents) =>
			if (!entry.isDirectory && entry.getName.endsWith(".class")) {
				val methods = AsmVisitors.parseMethodsFromClass(contents)
				for {
					(name, size) <- methods
					treeNode <- builder.getOrAddMethod(name, size)
				} methodCorrelationsBuilder += (name -> treeNode.id)
			}
		}
		val treemapNodes = builder.condensePathNodes().result
		val methodCorrelations = methodCorrelationsBuilder.result

		if (treemapNodes.isEmpty) None else Some {
			val traceId = traceManager.createTrace
			val traceData = traceDataProvider getTrace traceId

			// Note: the `creationDate` for trace data detected in this manner
			// should use its default value ('now'), as this is when the data
			// was actually 'created'.

			traceData.treeNodeData.storeNodes(treemapNodes)
			traceData.treeNodeData.mapMethodSignatures(methodCorrelations)

			traceId
		}
	}

	def handleUpload(file: File): Box[TraceId] = {
		handleBinaryZip(file) or Failure("Invalid upload file")
	}

}


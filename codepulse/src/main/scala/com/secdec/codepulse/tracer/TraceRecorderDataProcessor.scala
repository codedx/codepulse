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

import com.codedx.codepulse.hq.data.processing.DataProcessor
import com.codedx.codepulse.hq.protocol.DataMessageContent
import com.secdec.codepulse.data.MethodSignature
import com.secdec.codepulse.data.MethodSignatureParser
import com.secdec.codepulse.data.model.ProjectData
import com.secdec.codepulse.data.jsp.JspMapper

class TraceRecorderDataProcessor(projectData: ProjectData, transientData: TransientTraceData, jspMapper: Option[JspMapper]) extends DataProcessor {

	val methodCor = collection.mutable.Map[Int, Int]()

	/** Process a single data message */
	def processMessage(message: DataMessageContent): Unit = message match {

		// handle method encounters
		case DataMessageContent.MethodEntry(methodId, timestamp, _) =>
			val runningRecordings = projectData.recordings.all.filter(_.running).map(_.id)
			println(s"Entered method $methodId")
			for (nodeId <- methodCor get methodId) {
				projectData.encounters.record(runningRecordings, nodeId :: Nil)
				transientData addEncounter nodeId
			}

		// make method correlations
		case DataMessageContent.MapMethodSignature(sig, id) =>
			println(s"Method $id -> Signature: $sig")
			val node = projectData.treeNodeData.getNodeIdForSignature(sig).orElse(jspMapper.flatMap(_ map sig))
			for (treemapNodeId <- node) methodCor.put(id, treemapNodeId)

		case DataMessageContent.MapSourceLocation(sig, startLine, endLine, startCharacter, endCharacter, sourceLocationId) =>
			println(s"Source Location $sourceLocationId -> MethodId: $sig Lines: $startLine-$endLine Characters: $startCharacter $endCharacter")

		case DataMessageContent.MethodVisit(methodId, sourceLocationId, _, _) =>
			println(s"Visited method $methodId at location $sourceLocationId")

		// ignore everything else
		case _ => ()
	}

	/** Process a break in the data */
	def processDataBreak(): Unit = ()

	/** There is no more data, so do any cleanup/saving/etc necessary */
	def finishProcessing(): Unit = ()

	def cleanup() = ()
}
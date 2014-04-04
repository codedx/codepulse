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

import com.secdec.bytefrog.hq.data.processing.DataProcessor
import com.secdec.bytefrog.hq.protocol.DataMessageContent
import com.secdec.codepulse.data.MethodSignature
import com.secdec.codepulse.data.MethodSignatureParser
import com.secdec.codepulse.data.trace.TraceData
import com.secdec.codepulse.data.jsp.JasperJspMapper

class TraceRecorderDataProcessor(traceData: TraceData, transientData: TransientTraceData) extends DataProcessor {

	val methodCor = collection.mutable.Map[Int, Int]()

	val jspMapper = JasperJspMapper(traceData.treeNodeData)

	/** Process a single data message */
	def processMessage(message: DataMessageContent): Unit = message match {

		// handle method encounters
		case DataMessageContent.MethodEntry(methodId, timestamp, _) =>
			val runningRecordings = traceData.recordings.all.filter(_.running).map(_.id)

			for (nodeId <- methodCor get methodId) {
				traceData.encounters.record(runningRecordings, nodeId :: Nil)
				transientData addEncounter nodeId
			}

		// make method correlations
		case DataMessageContent.MapMethodSignature(sig, id) =>
			if (sig.startsWith("org/apache"))
				println(s"looking at $sig")
			val node = traceData.treeNodeData.getNodeIdForSignature(sig).orElse(jspMapper map sig)
			if (sig.startsWith("org/apache"))
				println(node)
			for (treemapNodeId <- node) methodCor.put(id, treemapNodeId)

		// ignore everything else
		case _ => ()
	}

	/** Process a break in the data */
	def processDataBreak(): Unit = ()

	/** There is no more data, so do any cleanup/saving/etc necessary */
	def finishProcessing(): Unit = ()

	def cleanup() = ()
}
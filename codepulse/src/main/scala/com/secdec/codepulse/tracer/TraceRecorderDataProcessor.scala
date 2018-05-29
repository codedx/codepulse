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
import com.codedx.codepulse.utility.Loggable
import com.secdec.codepulse.data.model.ProjectData
import com.secdec.codepulse.data.jsp.JspMapper

case class DeferredMapSourceLocation(sourceLocationId: Int, message: DataMessageContent.MapSourceLocation)

case class DeferredMethodVisit(methodId: Int, message: DataMessageContent.MethodVisit)

case class DeferredMethodEntry(methodId: Int, message: DataMessageContent.MethodEntry)

class TraceRecorderDataProcessor(projectData: ProjectData, transientData: TransientTraceData, jspMapper: Option[JspMapper]) extends DataProcessor with Loggable {

	val methodCor = collection.mutable.Map[Int, Int]()
	val unknownAndIgnoredMethodCor = collection.mutable.Set[Int]()
	val sourceLocationCor = collection.mutable.Map[Int, Option[Int]]()

	val deferredMethodEntries = collection.mutable.Map.empty[Int, collection.mutable.ListBuffer[DataMessageContent.MethodEntry]]
	val deferredMapSourceLocations = collection.mutable.Map.empty[Int, collection.mutable.ListBuffer[DataMessageContent.MapSourceLocation]]
	val deferredMethodVisits = collection.mutable.Map.empty[Int, collection.mutable.ListBuffer[DataMessageContent.MethodVisit]]

	/** Process a single data message */
	def processMessage(message: DataMessageContent): Unit = {

		message match {

			// handle method encounters
			case methodEntryMessage @ DataMessageContent.MethodEntry(methodId, timestamp, _) => {

				if (methodCor.get(methodId).nonEmpty) {
					methodVisit(methodId, None)
					return
				}

				logger.info(s"Deferring method entry for unknown method $methodId...")

				var methodEntries = deferredMethodEntries.get(methodId)
				if (methodEntries.isEmpty) {
					methodEntries = Option(collection.mutable.ListBuffer.empty[DataMessageContent.MethodEntry])
					deferredMethodEntries.put(methodId, methodEntries.get)
				}
				methodEntries.get.append(methodEntryMessage)
			}

			// make method correlations
			case DataMessageContent.MapMethodSignature(sig, id) =>
				val node = projectData.treeNodeData.getNodeIdForSignature(sig).orElse(jspMapper.flatMap(_ map sig))
				if (node.isEmpty) {
					logger.warn(s"*** Ignoring signature missing from application inventory: $sig")
					unknownAndIgnoredMethodCor.add(id)
				}
				else {
					for (treemapNodeId <- node) {
						methodCor.put(id, treemapNodeId)
						deferredMethodEntries.remove(id).getOrElse(collection.mutable.ListBuffer.empty[DataMessageContent.MethodEntry]).foreach(x => {
							logger.info(s"Processing deferred method entry for method ${x.methodId}...")
							processMessage(x)
						})
						deferredMapSourceLocations.remove(id).getOrElse(collection.mutable.ListBuffer.empty[DataMessageContent.MapSourceLocation]).foreach(x => {
							logger.info(s"Processing deferred map source location for source location ${x.sourceLocationId} in method ${x.methodId}...")
							processMessage(x)
						})
					}
				}

			case mapSourceLocationMessage @ DataMessageContent.MapSourceLocation(methodId, startLine, endLine, startCharacter, endCharacter, id) =>
				val nodeId = methodCor.get(methodId)
				if (nodeId.isEmpty) {
					if (unknownAndIgnoredMethodCor.contains(methodId)) return

					logger.info(s"Deferring map source location $id for unknown method $methodId...")

					var methodMapSourceLocations = deferredMapSourceLocations.get(methodId)
					if (methodMapSourceLocations.isEmpty) {
						methodMapSourceLocations = Option(collection.mutable.ListBuffer.empty[DataMessageContent.MapSourceLocation])
						deferredMapSourceLocations.put(methodId, methodMapSourceLocations.get)
					}
					methodMapSourceLocations.get.append(mapSourceLocationMessage)
				}
				else {
					var startChar: Option[Int] = None
					if (startCharacter != -1) startChar = Option(startCharacter.toInt)

					var endChar: Option[Int] = None
					if (endCharacter != -1) endChar = Option(endCharacter.toInt)

					var sourceLocationIdFromDatabase: Option[Int] = None
					val sourceFileId = projectData.treeNodeData.getNode(nodeId.get).get.sourceFileId
					if (!sourceFileId.isEmpty) {
						sourceLocationIdFromDatabase = Option(projectData.sourceData.getSourceLocationId(sourceFileId.get, startLine, endLine, startChar, endChar))
					}

					sourceLocationCor.put(id, sourceLocationIdFromDatabase)

					deferredMethodVisits.remove(id).getOrElse(collection.mutable.ListBuffer.empty[DataMessageContent.MethodVisit]).foreach(x => {
						logger.info(s"Processing deferred method visit for source location ${x.sourceLocationId} in method ${x.methodId}")
						processMessage(x)
					})
				}

			case methodVisitMessage @ DataMessageContent.MethodVisit(methodId, sourceLocationId, _, _) =>
				val mappedId = sourceLocationCor.get(sourceLocationId)
				if (mappedId.nonEmpty) {
					methodVisit(methodId, mappedId.get)
					return
				}

				logger.info(s"Deferring method visit for unknown source location $sourceLocationId of method $methodId...")

				var sourceLocationMethodVisits = deferredMethodVisits.get(sourceLocationId)
				if (sourceLocationMethodVisits.isEmpty) {
					sourceLocationMethodVisits = Option(collection.mutable.ListBuffer.empty[DataMessageContent.MethodVisit])
					deferredMethodVisits.put(sourceLocationId, sourceLocationMethodVisits.get)
				}
				sourceLocationMethodVisits.get.append(methodVisitMessage)

			// ignore everything else
			case _ => ()
		}
	}

	/** Process a break in the data */
	def processDataBreak(): Unit = ()

	/** There is no more data, so do any cleanup/saving/etc necessary */
	def finishProcessing(): Unit = ()

	def cleanup() = ()

	def methodVisit(methodId: Int,  sourceLocationId: Option[Int]): Unit = {
		val runningRecordings = projectData.recordings.all.filter (_.running).map (_.id)
		for (nodeId <- methodCor get methodId) {
			projectData.encounters.record (runningRecordings, (nodeId, sourceLocationId) :: Nil)
			transientData addEncounter nodeId
		}
	}
}
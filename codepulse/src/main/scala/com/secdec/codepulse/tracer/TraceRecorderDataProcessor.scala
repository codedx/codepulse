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

	// list of client-side node IDs that are ignored for tracing because they refer to method signatures missing from the database
	val unknownAndIgnoredMethodCor = collection.mutable.Set[Int]()

	// links client-side node ID representing a signature to a list of server-side node IDs sharing the same method signature
	val methodCor = new collection.mutable.HashMap[Int, collection.mutable.Set[Int]] with collection.mutable.MultiMap[Int, Int]

	// links client-side source location ID to a node-specific server-side source location ID
	val sourceLocationCor = new collection.mutable.HashMap[Int, collection.mutable.HashMap[Int, Option[Int]]]

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
				var nodeIds = projectData.treeNodeData.getNodeIdsForSignature(sig)
				if (nodeIds.isEmpty) {
					val jspNodeId = jspMapper.flatMap(_ map sig)
					if (jspNodeId.nonEmpty) {
						nodeIds = jspNodeId.get :: Nil
					}
				}

				if (nodeIds.isEmpty) {
					logger.warn(s"*** Ignoring signature missing from application inventory: $sig")
					unknownAndIgnoredMethodCor.add(id)
					return
				}

				nodeIds.foreach(nodeId => {
					methodCor.addBinding(id, nodeId)
				})

				deferredMethodEntries.remove(id).getOrElse(collection.mutable.ListBuffer.empty[DataMessageContent.MethodEntry]).foreach(x => {
					logger.info(s"Processing deferred method entry for method ${x.methodId}...")
					processMessage(x)
				})
				deferredMapSourceLocations.remove(id).getOrElse(collection.mutable.ListBuffer.empty[DataMessageContent.MapSourceLocation]).foreach(x => {
					logger.info(s"Processing deferred map source location for source location ${x.sourceLocationId} in method ${x.methodId}...")
					processMessage(x)
				})

			case mapSourceLocationMessage @ DataMessageContent.MapSourceLocation(methodId, startLine, endLine, startCharacter, endCharacter, id) =>
				val nodeIds = methodCor.get(methodId)
				if (nodeIds.isEmpty) {
					if (unknownAndIgnoredMethodCor.contains(methodId)) return

					logger.info(s"Deferring map source location $id for unknown method $methodId...")

					var methodMapSourceLocations = deferredMapSourceLocations.get(methodId)
					if (methodMapSourceLocations.isEmpty) {
						methodMapSourceLocations = Option(collection.mutable.ListBuffer.empty[DataMessageContent.MapSourceLocation])
						deferredMapSourceLocations.put(methodId, methodMapSourceLocations.get)
					}
					methodMapSourceLocations.get.append(mapSourceLocationMessage)
					return
				}

				nodeIds.get.foreach(nodeId => {
					var startChar: Option[Int] = None
					if (startCharacter != -1) startChar = Option(startCharacter.toInt)

					var endChar: Option[Int] = None
					if (endCharacter != -1) endChar = Option(endCharacter.toInt)

					var sourceLocationIdFromDatabase: Option[Int] = None
					val sourceFileId = projectData.treeNodeData.getNode(nodeId).get.sourceFileId
					if (!sourceFileId.isEmpty) {
						sourceLocationIdFromDatabase = Option(projectData.sourceData.getSourceLocationId(sourceFileId.get, startLine, endLine, startChar, endChar))
					}

					var mapping = sourceLocationCor.get(id)
					if (mapping.isEmpty) {
						mapping = Option(new collection.mutable.HashMap[Int, Option[Int]])
						sourceLocationCor.put(id, mapping.get)
					}
					mapping.get.put(nodeId, sourceLocationIdFromDatabase)
				})

				deferredMethodVisits.remove(id).getOrElse(collection.mutable.ListBuffer.empty[DataMessageContent.MethodVisit]).foreach(x => {
					logger.info(s"Processing deferred method visit for source location ${x.sourceLocationId} in method ${x.methodId}")
					processMessage(x)
				})


			case methodVisitMessage @ DataMessageContent.MethodVisit(methodId, sourceLocationId, _, _) =>
				val mappedIds = sourceLocationCor.get(sourceLocationId)
				if (mappedIds.isEmpty) {
					logger.info(s"Deferring method visit for unknown source location $sourceLocationId of method $methodId...")

					var sourceLocationMethodVisits = deferredMethodVisits.get(sourceLocationId)
					if (sourceLocationMethodVisits.isEmpty) {
						sourceLocationMethodVisits = Option(collection.mutable.ListBuffer.empty[DataMessageContent.MethodVisit])
						deferredMethodVisits.put(sourceLocationId, sourceLocationMethodVisits.get)
					}
					sourceLocationMethodVisits.get.append(methodVisitMessage)
					return
				}

				methodVisit(methodId, Option(sourceLocationId))

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

		var sourceLocationsByNode = collection.mutable.HashMap.empty[Int, Option[Int]]
		if (sourceLocationId.nonEmpty) {
			sourceLocationsByNode = sourceLocationCor.get(sourceLocationId.get).getOrElse(sourceLocationsByNode)
		}

		val runningRecordings = projectData.recordings.all.filter (_.running).map (_.id)
		for {
			nodeIds <- methodCor get methodId
			nodeId <- nodeIds
		}{
			projectData.encounters.record (runningRecordings, (nodeId, sourceLocationsByNode.get(nodeId).getOrElse(None)) :: Nil)
			transientData addEncounter nodeId
		}
	}
}
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

package com.secdec.codepulse.tracer.export

import java.io.InputStream
import java.util.zip.ZipFile

import com.fasterxml.jackson.core.JsonToken
import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.trace._

import net.liftweb.util.Helpers.AsInt

/** Reader for version 1 of the .pulse trace export files.
  *
  * @author robertf
  */
object TraceImportReaderV1 extends TraceImportReader with TraceImportHelpers with JsonHelpers {
	def doImport(zip: ZipFile, destination: TraceData) {
		read(zip, "trace.json") { readMetadata(_, destination.metadata) }
		read(zip, "nodes.json") { readTreeNodeData(_, destination.treeNodeData) }
		read(zip, "method-correlations.json") { readMethodCorrelations(_, destination.treeNodeData) }
		val recMap = read(zip, "recordings.json", Map.empty[Int, Int]) { readRecordings(_, destination.recordings) }
		read(zip, "encounters.json") { readEncounters(_, recMap, destination.encounters) }
	}

	private def readMetadata(in: InputStream, metadata: TraceMetadataAccess) {
		import JsonToken._

		readJson(in) { jp =>
			if (jp.nextToken != START_OBJECT)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

			while (jp.nextValue != END_OBJECT) {
				jp.getCurrentName match {
					case "name" => metadata.name = jp.getText
					case "creationDate" => metadata.creationDate = jp.getLongValue

					case "importDate" => metadata.importDate = jp.getCurrentToken match {
						case VALUE_NULL => None
						case _ => Some(jp.getLongValue)
					}

					case other => throw new TraceImportException(s"Unrecognized field $other.")
				}
			}

			if (jp.nextToken != null)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
		}
	}

	private def readTreeNodeData(in: InputStream, treeNodeData: TreeNodeDataAccess) {
		import JsonToken._

		readJson(in) { jp =>
			if (jp.nextToken != START_ARRAY)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_ARRAY.")

			val buffer = collection.mutable.ListBuffer.empty[TreeNode]
			def flushBuffer() { treeNodeData.storeNodes(buffer); buffer.clear }
			def checkAndFlush() { if (buffer.size >= 500) flushBuffer }

			while (jp.nextValue != END_ARRAY) {
				if (jp.getCurrentToken != START_OBJECT)
					throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

				var id = None: Option[Int]
				var parentId = None: Option[Int]
				var label = None: Option[String]
				var kind = None: Option[CodeTreeNodeKind]
				var size = None: Option[Int]

				while (jp.nextValue != END_OBJECT) {
					jp.getCurrentName match {
						case "id" => id = Some(jp.getIntValue)

						case "parentId" =>
							parentId = jp.getCurrentToken match {
								case VALUE_NULL => None
								case _ => Some(jp.getIntValue)
							}

						case "label" => label = Some(jp.getText)
						case "kind" => kind = CodeTreeNodeKind.unapply(jp.getText)

						case "size" =>
							size = jp.getCurrentToken match {
								case VALUE_NULL => None
								case _ => Some(jp.getIntValue)
							}
					}
				}

				buffer += TreeNode(
					id getOrElse { throw new TraceImportException("Missing ID for tree node.") },
					parentId,
					label getOrElse { throw new TraceImportException("Missing label for tree node.") },
					kind.getOrElse { throw new TraceImportException("Missing or invalid kind for tree node.") },
					size)

				checkAndFlush
			}

			flushBuffer

			if (jp.nextToken != null)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
		}
	}

	private def readMethodCorrelations(in: InputStream, treeNodeData: TreeNodeDataAccess) {
		import JsonToken._

		readJson(in) { jp =>
			if (jp.nextToken != START_ARRAY)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_ARRAY.")

			val buffer = collection.mutable.ListBuffer.empty[(String, Int)]
			def flushBuffer() { treeNodeData.mapMethodSignatures(buffer); buffer.clear }
			def checkAndFlush() { if (buffer.size >= 500) flushBuffer }

			while (jp.nextValue != END_ARRAY) {
				if (jp.getCurrentToken != START_OBJECT)
					throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

				var signature = None: Option[String]
				var nodeId = None: Option[Int]

				while (jp.nextValue != END_OBJECT) {
					jp.getCurrentName match {
						case "signature" => signature = Some(jp.getText)
						case "node" => nodeId = Some(jp.getIntValue)
					}
				}

				buffer += ((
					signature getOrElse { throw new TraceImportException("Missing signature for method correlation.") },
					nodeId getOrElse { throw new TraceImportException("Missing node ID for method correlation.") }))

				checkAndFlush
			}

			flushBuffer

			if (jp.nextToken != null)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
		}
	}

	private def readRecordings(in: InputStream, recordings: RecordingMetadataAccess) = {
		import JsonToken._

		var idMap = collection.mutable.Map.empty[Int, Int]

		readJson(in) { jp =>
			if (jp.nextToken != START_ARRAY)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_ARRAY.")

			while (jp.nextValue != END_ARRAY) {
				if (jp.getCurrentToken != START_OBJECT)
					throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

				var id = None: Option[Int]
				var running = None: Option[Boolean]
				var label = None: Option[String]
				var color = None: Option[String]

				while (jp.nextValue != END_OBJECT) {
					jp.getCurrentName match {
						case "id" => id = Some(jp.getIntValue)
						case "running" => running = Some(jp.getBooleanValue)

						case "label" =>
							label = jp.getCurrentToken match {
								case VALUE_NULL => None
								case _ => Some(jp.getText)
							}

						case "color" =>
							color = jp.getCurrentToken match {
								case VALUE_NULL => None
								case _ => Some(jp.getText)
							}
					}
				}

				val oldId = id getOrElse { throw new TraceImportException("Missing ID for recording.") }
				val newRec = recordings.create
				idMap += oldId -> newRec.id

				newRec.running = running getOrElse false
				newRec.clientLabel = label
				newRec.clientColor = color
			}

			if (jp.nextToken != null)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
		}

		idMap.toMap
	}

	private def readEncounters(in: InputStream, recordingMap: Map[Int, Int], encounters: TraceEncounterDataAccess) {
		import JsonToken._

		readJson(in) { jp =>
			if (jp.nextToken != START_OBJECT)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_ARRAY.")

			var all = Nil: List[Int]
			var inRec = collection.mutable.Set.empty[Int]

			while (jp.nextValue != END_OBJECT) {
				val rec = jp.getCurrentName

				if (jp.getCurrentToken != START_ARRAY)
					throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

				val values = collection.mutable.ListBuffer.empty[Int]

				while (jp.nextValue != END_ARRAY) {
					values += jp.getIntValue
				}

				rec match {
					case "all" => all = values.result

					case AsInt(recId) =>
						val result = values.result
						inRec ++= result
						encounters.record(recId :: Nil, result)

					case _ => throw new TraceImportException("Invalid recording ID for encounters map.")
				}
			}

			// we only need to store (all - inRec)
			encounters.record(Nil, (all.toSet -- inRec).toList)

			if (jp.nextToken != null)
				throw new TraceImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
		}
	}
}
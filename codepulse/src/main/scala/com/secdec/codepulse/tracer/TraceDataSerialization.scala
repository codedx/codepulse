///*
// * Code Pulse: A real-time code coverage testing tool. For more information
// * see http://code-pulse.com
// *
// * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.secdec.codepulse.tracer
//
//import java.io._
//import java.util.zip.ZipEntry
//import java.util.zip.ZipFile
//import java.util.zip.ZipOutputStream
//import net.liftweb.json.DefaultFormats
//import net.liftweb.json.JsonAST._
//import net.liftweb.json.JsonDSL._
//import net.liftweb.json.JsonParser
//import net.liftweb.json.Printer
//import net.liftweb.util.Helpers.AsInt
//import java.util.zip.ZipInputStream
//import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
//import com.secdec.codepulse.data.trace.TreeNode
//
///** Object that is responsible for serializing and deserializing TraceData instances.
//  * The serialized format for a TraceData is JSON, compressed in a Zip file, in the
//  * "trace-data.json" zip entry.
//  */
//object TraceDataSerialization {
//
//	implicit val formats = DefaultFormats
//
//	def treemapToJson(nodes: Iterable[TreeNode]): JObject = {
//		val fields = nodes.map { node =>
//			val nodeObj = ("name" -> node.label) ~
//				("kind" -> node.kind.label) ~
//				("lineCount" -> node.size) ~
//				("parentId" -> node.parentId)
//			JField(node.id.toString, nodeObj)
//		}
//		JObject(fields.toList)
//	}
//
//	def treemapFromJson(json: JObject): List[TreeNode] = {
//		json.obj flatMap {
//			case JField(AsInt(nodeId), nodeJson) =>
//				for {
//					label <- (nodeJson \ "name").extractOpt[String]
//					CodeTreeNodeKind(kind) <- (nodeJson \ "kind").extractOpt[String]
//				} yield {
//					val lineCount = (nodeJson \ "lineCount").extractOpt[Int]
//					val parentId = (nodeJson \ "parentId").extractOpt[Int]
//					TreeNode(nodeId, parentId, label, kind, lineCount)
//				}
//			case _ => None
//		}
//	}
//
//	def methodCorrelationsToJson(correlations: TraversableOnce[(String, Int)]): JObject = {
//		val fields = correlations map {
//			case (sig, nodeId) => JField(sig, JInt(nodeId))
//		}
//		JObject(fields.toList)
//	}
//
//	def methodCorrelationsFromJson(json: JObject): List[(String, Int)] = {
//		json.obj flatMap {
//			case JField(sig, JInt(nodeId)) => Some(sig -> nodeId.intValue)
//			case _ => None
//		}
//	}
//
//	def recordingsToJson(recordings: Iterable[TraceRecording]): JObject = {
//		val fields = recordings map { rec =>
//			val recObj = ("id" -> rec.id) ~
//				("running" -> rec.running) ~
//				("label" -> rec.clientLabel) ~
//				("color" -> rec.clientColor)
//			JField(rec.id.toString, recObj)
//		}
//		JObject(fields.toList)
//	}
//
//	def recordingsFromJson(json: JObject): List[TraceRecording] = {
//		json.obj flatMap {
//			case JField(AsInt(recId), recJson) =>
//				for {
//					running <- (recJson \ "running").extractOpt[Boolean]
//				} yield {
//					val label = (recJson \ "label").extractOpt[String]
//					val color = (recJson \ "color").extractOpt[String]
//					val rec = new TraceRecording(recId)
//					rec.running = running
//					rec.clientLabel = label
//					rec.clientColor = color
//					rec
//				}
//			case _ => None
//		}
//	}
//
//	def recordingCoverageToJson(coverage: TraversableOnce[(Int, TraversableOnce[Int])]): JObject = {
//		val fields = coverage map {
//			case (recordingId, coveredNodeIds) =>
//				// convert coveredNodeIds to a json array of ints
//				val coveredNodesJson = JArray(coveredNodeIds.map { id => id: JInt }.toList)
//				JField(recordingId.toString, coveredNodesJson)
//		}
//		JObject(fields.toList)
//	}
//
//	def recordingCoverageFromJson(json: JObject): Traversable[(Int, Traversable[Int])] = {
//		json.obj flatMap {
//			case JField(AsInt(recId), JArray(coveredNodeJsonIds)) =>
//				val coveredNodeIds = coveredNodeJsonIds flatMap { _.extractOpt[Int] }
//				Some(recId -> coveredNodeIds)
//			case _ => None
//		}
//	}
//
//	def totalCoverageToJson(coverage: TraversableOnce[Int]): JArray = {
//		JArray(coverage.map { id => id: JInt }.toList)
//	}
//
//	def totalCoverageFromJson(json: JArray): List[Int] = {
//		json.arr flatMap { _.extractOpt[Int] }
//	}
//
//	def traceDataToJson(traceData: TraceData): JObject = {
//		val nameJson = JString(traceData.name)
//		val treemapJson = treemapToJson(traceData.treeNodes)
//		val correlationsJson = methodCorrelationsToJson(traceData.methodCorrelations)
//		val recordingsJson = recordingsToJson(traceData.recordings)
//		val recordingCoverageJson = recordingCoverageToJson { traceData.encounteredNodesByRecordingId }
//		val totalCoverageJson = totalCoverageToJson { traceData.getAllEncounteredNodes }
//
//		("name" -> nameJson) ~
//			("creationDate" -> traceData.creationDate) ~
//			("importDate" -> traceData.importDate) ~
//			("treemap" -> treemapJson) ~
//			("methodCorrelations" -> correlationsJson) ~
//			("recordings" -> recordingsJson) ~
//			("recordingCoverage" -> recordingCoverageJson) ~
//			("totalCoverage" -> totalCoverageJson)
//	}
//
//	def traceDataFromJson(json: JValue) = {
//		for {
//			name <- (json \ "name").extractOpt[String]
//			creationDate <- (json \ "creationDate").extractOpt[Long]
//			treemapJson <- (json \ "treemap").extractOpt[JObject]
//			correlationsJson <- (json \ "methodCorrelations").extractOpt[JObject]
//			recordingsJson <- (json \ "recordings").extractOpt[JObject]
//			recordingCoverageJson <- (json \ "recordingCoverage").extractOpt[JObject]
//			totalCoverageJson <- (json \ "totalCoverage").extractOpt[JArray]
//		} yield {
//			val treemap = treemapFromJson(treemapJson)
//			val methodCorrelations = methodCorrelationsFromJson(correlationsJson)
//			val recordings = recordingsFromJson(recordingsJson)
//			val recordingCoverage = recordingCoverageFromJson(recordingCoverageJson)
//			val totalCoverage = totalCoverageFromJson(totalCoverageJson)
//
//			val traceData = new TraceData
//			traceData.name = name
//			traceData.creationDate = creationDate
//
//			traceData.importDate = (json \ "importDate").extractOpt[Long]
//
//			traceData.addOrUpdateTreeNodes(treemap)
//			traceData.addMethodSignatureCorrelations(methodCorrelations)
//			recordings foreach traceData.addRecording
//			for ((recId, encounters) <- recordingCoverage) traceData.addRecordingEncounters(recId, encounters)
//			totalCoverage foreach { traceData.setLatestNodeEncounterTime(_, 0) }
//			traceData.clearDirtyWith()
//			traceData
//		}
//	}
//
//	def traceDataToWriter(traceData: TraceData, writer: Writer) = {
//		val json = traceDataToJson(traceData)
//		val doc = render(json) // imported from JsonAST._
//		Printer.pretty(doc, writer)
//
//		writer
//	}
//
//	def traceDataFromReader(reader: Reader) = {
//		JsonParser.parseOpt(reader, false) flatMap traceDataFromJson
//	}
//
//	def traceDataToZip(traceData: TraceData, file: File) = {
//		val ostr = new BufferedOutputStream(new FileOutputStream(file))
//		val zipstr = new ZipOutputStream(ostr)
//
//		val entry = new ZipEntry("trace-data.json")
//		zipstr.putNextEntry(entry)
//
//		val writer = new OutputStreamWriter(zipstr)
//		traceDataToWriter(traceData, writer)
//		writer.flush
//
//		zipstr.closeEntry()
//		zipstr.close()
//
//		file
//	}
//
//	def traceDataFromZip(file: File) = {
//		val zip = new ZipFile(file)
//		try {
//			val entryReader = Option { zip.getEntry("trace-data.json") } map { entry =>
//				val stream = zip.getInputStream(entry)
//				new InputStreamReader(new BufferedInputStream(stream))
//			}
//			entryReader flatMap traceDataFromReader
//		} finally {
//			zip.close()
//		}
//	}
//
//	def traceDataFromZipStream(stream: InputStream): Option[TraceData] = {
//		val zipstr = new ZipInputStream(stream)
//		try {
//			val entry = zipstr.getNextEntry()
//			if (entry.getName == "trace-data.json") {
//				val reader = new InputStreamReader(zipstr)
//				val result = traceDataFromReader(reader)
//				zipstr.closeEntry
//				result
//			} else {
//				None
//			}
//		} finally {
//			zipstr.close()
//		}
//	}
//
//	def main(args: Array[String]): Unit = {
//
//		import CodeTreeNodeKind._
//		val treemap = List(
//			TreeNode(0, None, "zero", Pkg, None),
//			TreeNode(1, Some(0), "one", Cls, None),
//			TreeNode(2, Some(1), "two", Mth, Some(15)),
//			TreeNode(3, Some(1), "three", Mth, Some(7)))
//
//		val rec0 = new TraceRecording(12)
//		rec0.clientColor = Some("#abcdef")
//
//		val rec1 = new TraceRecording(34)
//		rec1.running = false
//		rec1.clientLabel = Some("label1")
//
//		val recordings = List(rec0, rec1)
//
//		val traceData = new TraceData
//		traceData.addOrUpdateTreeNodes(treemap)
//		recordings foreach traceData.addRecording
//		traceData.addNewRecording.clientLabel = Some("extra thing")
//
//		List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) foreach { traceData.setLatestNodeEncounterTime(_, 0) }
//		traceData.addRecordingEncounters(12, List(3, 5, 7, 10))
//		traceData.addRecordingEncounters(34, List(1, 2, 3, 7, 8, 9))
//
//		val zipFile = traceDataToZip(traceData, new File(System.getProperty("user.home") + "/trace-data-test.zip"))
//		val traceDataRe = traceDataFromZip(zipFile)
//
//		println("recording coverage: " + traceData.encounteredNodesByRecordingId)
//		println("total coverage: " + traceData.getAllEncounteredNodes)
//		println("recordings: " + traceData.recordings.toList)
//		println("treenodes: " + traceData.treeNodes.toList)
//	}
//
//}
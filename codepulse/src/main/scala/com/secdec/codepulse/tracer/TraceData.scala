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

import scala.Option.option2Iterable
import scala.collection.mutable.{ Map => MutableMap }
import scala.collection.mutable.{ Set => MutableSet }
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import reactive.EventSource
import reactive.EventStream

/** Represents a node in the code treemap.
  *
  * @param id The node's identifier, which should be unique within a tree.
  * @param parentId The id of the node's optional parent.
  * @param label The user-facing name of the node
  * @param kind Either "package", "class", or "method", depending on what the code was
  * @param size A number indicating the size of the node (e.g. lines of code). If unspecified,
  * the size of a node is assumed to be the sum of its childrens' sizes.
  */
case class TreeNode(id: Int, parentId: Option[Int], label: String, kind: CodeTreeNodeKind, size: Option[Int]) {
	def asJsonField = {
		val fields = List(
			parentId map { JField("parentId", _) },
			Some { JField("name", label) },
			Some { JField("kind", kind.label) },
			size map { JField("lineCount", _) } //
			).flatten
		JField(id.toString, JObject(fields))
	}
}

trait HasDirtyFlag {
	private var dirty = false

	def markDirty() = {
		dirty = true
	}

	def clearDirtyWith[U](f: => U): Unit = {
		if (dirty) {
			f
			dirty = false
		}
	}
}

trait HasTraceMetadata extends HasDirtyFlag {
	private var _name = "Untitled"
	private var _creationDate = System.currentTimeMillis
	private var _importDate: Option[Long] = None
	private val _nameChanges = new EventSource[String]

	def name = _name
	def name_=(newName: String) = {
		_name = newName
		_nameChanges fire _name
		markDirty()
	}

	def nameChanges: EventStream[String] = _nameChanges

	def creationDate = _creationDate
	def creationDate_=(d: Long): Unit = {
		_creationDate = d
		markDirty()
	}

	def importDate = _importDate
	def importDate_=(d: Option[Long]) = {
		_importDate = d
		markDirty()
	}

}

trait HasTreeNodeData extends HasDirtyFlag {
	val treeNodesById = MutableMap.empty[Int, TreeNode]
	val methodCorrelations = MutableMap.empty[String, Int]

	def treeNodes: Iterable[TreeNode] = {
		treeNodesById.values
	}

	def methodSignatures: Iterable[String] = {
		methodCorrelations.keys
	}

	def getTreeNode(nodeId: Int): Option[TreeNode] = {
		treeNodesById.get(nodeId)
	}

	def getMappedNodeId(methodSignature: String): Option[Int] = {
		methodCorrelations get methodSignature
	}

	def addOrUpdateTreeNodes(nodes: TraversableOnce[TreeNode]): Unit = {
		for (node <- nodes) treeNodesById.update(node.id, node)
		markDirty()
	}

	def addMethodSignatureCorrelations(sigsWithIds: TraversableOnce[(String, Int)]): Unit = {
		for { (sig, nodeId) <- sigsWithIds } methodCorrelations.put(sig, nodeId)
		markDirty()
	}
}

trait HasTimedEncounterData extends HasDirtyFlag {
	val latestEncounterTimesByNodeId = MutableMap.empty[Int, Long]

	@inline def now = System.currentTimeMillis

	def setLatestNodeEncounterTime(nodeId: Int, timestamp: Long): Unit = {
		latestEncounterTimesByNodeId.update(nodeId, timestamp)
		markDirty()
	}

	def getNodesEncounteredAfterTime(time: Long): Iterable[Int] = {
		for {
			(nodeId, encounterTime) <- latestEncounterTimesByNodeId
			if encounterTime > time
		} yield nodeId
	}

	def getAllEncounteredNodes: collection.Set[Int] = {
		latestEncounterTimesByNodeId.keySet
	}
}

trait HasRecordingsAndTheirEncounters extends HasDirtyFlag {
	val recordingsById = MutableMap.empty[Int, TraceRecording]
	val encounteredNodesByRecordingId = MutableMap.empty[Int, MutableSet[Int]]

	def recordings: Iterable[TraceRecording] = recordingsById.values

	def addRecording(rec: TraceRecording): Unit = recordingsById.get(rec.id) match {
		case None =>
			recordingsById.put(rec.id, rec)
			encounteredNodesByRecordingId.put(rec.id, MutableSet.empty)
			markDirty()
		case Some(existing) =>
			throw new IllegalArgumentException(s"Already have a recording with id=${rec.id}")
	}

	def addNewRecording: TraceRecording = {
		val id =
			if (recordingsById.isEmpty) 0
			else recordingsById.keys.max + 1
		val rec = new TraceRecording(id)
		addRecording(rec)
		markDirty()
		rec
	}

	def getRecording(recordingId: Int): Option[TraceRecording] = {
		recordingsById get recordingId
	}

	def removeRecording(recordingId: Int): Option[TraceRecording] = {
		val removed = recordingsById.remove(recordingId)
		encounteredNodesByRecordingId.remove(recordingId)
		if (removed.isDefined) markDirty()
		removed
	}

	def addRecordingEncounters(recordingId: Int, encounteredNodeIds: TraversableOnce[Int]): Unit = {
		val encountersSet = encounteredNodesByRecordingId.getOrElseUpdate(recordingId, MutableSet.empty)
		encountersSet ++= encounteredNodeIds
		markDirty()
	}

	def getNodesEncounteredByRecording(recordingId: Int): collection.Set[Int] = {
		// note the return type of Set for expected O(1) `.size`
		encounteredNodesByRecordingId.getOrElse(recordingId, collection.Set.empty[Int])
	}

}

trait HasAccumulatingEncounterData extends HasDirtyFlag {
	val recentlyEncounteredNodes = MutableSet.empty[Int]

	def addRecentlyEncounteredNodes(nodeIds: TraversableOnce[Int]): Unit = {
		recentlyEncounteredNodes ++= nodeIds
		markDirty()
	}

	def getAndClearRecentlyEncounteredNodes[T](f: TraversableOnce[Int] => T): T = {
		val result = f(recentlyEncounteredNodes)
		if (!recentlyEncounteredNodes.isEmpty) markDirty()
		recentlyEncounteredNodes.clear()
		result
	}
}

class TraceData
	extends HasTraceMetadata
	with HasTreeNodeData
	with HasTimedEncounterData
	with HasRecordingsAndTheirEncounters
	with HasAccumulatingEncounterData {

	def reset(): this.type = {
		treeNodesById.clear()
		latestEncounterTimesByNodeId.clear()
		recordingsById.clear()
		encounteredNodesByRecordingId.clear()
		recentlyEncounteredNodes.clear()
		markDirty()
		this
	}

	def addEncounters(nodeIds: Traversable[Int]) = {
		nodeIds foreach { setLatestNodeEncounterTime(_, now) }
		for { (recordingId, rec) <- recordingsById if rec.running } {
			addRecordingEncounters(recordingId, nodeIds)
		}
		addRecentlyEncounteredNodes(nodeIds)
	}

}

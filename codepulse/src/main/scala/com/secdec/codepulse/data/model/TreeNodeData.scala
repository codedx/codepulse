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

package com.secdec.codepulse.data.model

import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind

case class MethodSignatureNode(id: Int, signature: String, nodeId: Int)

/** Represents a node in the code treemap.
  *
  * @param id The node's identifier, which should be unique within a tree.
  * @param parentId The id of the node's optional parent.
  * @param label The user-facing name of the node
  * @param kind The `CodeTreeNodeKind` of this node
  * @param size A number indicating the size of the node (e.g. lines of code). If unspecified,
  * the size of a node is assumed to be the sum of its childrens' sizes.
  */
case class TreeNodeData(id: Int, parentId: Option[Int], label: String, kind: CodeTreeNodeKind, size: Option[Int], sourceFileId: Option[Int], sourceLocationCount: Option[Int], methodStartLine: Option[Int], var isSurfaceMethod: Option[Boolean])

sealed trait TreeNodeFlag
object TreeNodeFlag {
	/** This flag signals that the node was tagged as having a vulnerability */
	case object HasVulnerability extends TreeNodeFlag
}

/** Access trait for tree node data.
  *
  * @author robertf
  */
trait TreeNodeDataAccess {
	def foreach(f: TreeNodeData => Unit): Unit
	def iterate[T](f: Iterator[TreeNodeData] => T): T

	def getNode(id: Int): Option[TreeNodeData]
	def getNode(id: Int, kind: CodeTreeNodeKind): Option[TreeNodeData]
	def getNode(label: String): Option[TreeNodeData]

	def getNodeIdsForSignature(signature: String): List[Int]
	def getNodesForSignature(signature: String): List[TreeNodeData]

	def foreachMethodMapping(f: MethodSignatureNode => Unit): Unit
	def iterateMethodMappings[T](f: Iterator[MethodSignatureNode] => T): T

	def mapMethodSignature(methodSignatureNode: MethodSignatureNode): Unit
	def mapMethodSignatures(signatures: Iterable[MethodSignatureNode]): Unit = signatures foreach { mapMethodSignature }

	def getNodeIdForJsp(jspClass: String): Option[Int]
	def getNodeForJsp(jspClass: String): Option[TreeNodeData]

	def foreachJspMapping(f: (String, Int) => Unit): Unit
	def iterateJspMappings[T](f: Iterator[(String, Int)] => T): T

	def mapJsp(jspClass: String, nodeId: Int): Unit
	def mapJsps(jsps: Iterable[(String, Int)]): Unit = jsps foreach { case (jspPath, nodeId) => mapJsp(jspPath, nodeId) }

	case class BulkImportElement(data: TreeNodeData, traced: Option[Boolean])
	def bulkImport(data: Iterable[BulkImportElement]): Unit

	def storeNode(node: TreeNodeData): Unit
	def storeNodes(nodes: Iterable[TreeNodeData]) = nodes foreach storeNode

	def isTraced(id: Int): Option[Boolean]
	def isTraced(node: TreeNodeData): Option[Boolean] = isTraced(node.id)

	def updateTraced(id: Int, traced: Option[Boolean]): Unit
	def updateTraced(id: Int, traced: Boolean): Unit = updateTraced(id, Some(traced))
	def updateTraced(node: TreeNodeData, traced: Option[Boolean]): Unit = updateTraced(node.id, traced)
	def updateTraced(node: TreeNodeData, traced: Boolean): Unit = updateTraced(node.id, Some(traced))
	def updateTraced(values: Iterable[(Int, Boolean)]): Unit = values foreach { case (id, traced) => updateTraced(id, Some(traced)) }

	def getFlags(id: Int): List[TreeNodeFlag]
	def getFlags(node: TreeNodeData): List[TreeNodeFlag] = getFlags(node.id)

	def setFlag(id: Int, flag: TreeNodeFlag): Unit
	def setFlag(node: TreeNodeData, flag: TreeNodeFlag): Unit = setFlag(node.id, flag)
	def clearFlag(id: Int, flag: TreeNodeFlag): Unit
	def clearFlag(node: TreeNodeData, flag: TreeNodeFlag): Unit = clearFlag(node.id, flag)

	def updateSourceLocationCount(id: Int, sourceLocationCount: Int)

	def findMethods(sourceFilePath: String): List[Int]
	def findMethods(sourceFilePath: String, startingLineNumber: Int, endingLineNumber: Int): List[Int]
	def markSurfaceMethod(id: Int, isSurfaceMethod: Option[Boolean])
	def getSurfaceMethodAncestorPackages(): List[Int]
	def getSurfaceMethodCount(): Int

	implicit class ExtendedTreeNodeData(n: TreeNodeData) {
		/** whether or not this treenode is being traced; this value may be unspecified (None) */
		def traced = isTraced(n)
		def traced_=(newVal: Boolean) = updateTraced(n, newVal)
		def traced_=(newVal: Option[Boolean]) = updateTraced(n, newVal)

		/** the flags set on this node */
		object flags {
			lazy val value = getFlags(n).toSet
			def contains(flag: TreeNodeFlag) = value contains flag
			def +=(flag: TreeNodeFlag) = setFlag(n, flag)
			def -=(flag: TreeNodeFlag) = clearFlag(n, flag)
		}
	}
}
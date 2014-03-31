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

package com.secdec.codepulse.data.trace

import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind

/** Represents a node in the code treemap.
  *
  * @param id The node's identifier, which should be unique within a tree.
  * @param parentId The id of the node's optional parent.
  * @param label The user-facing name of the node
  * @param kind The `CodeTreeNodeKind` of this node
  * @param size A number indicating the size of the node (e.g. lines of code). If unspecified,
  * the size of a node is assumed to be the sum of its childrens' sizes.
  */
case class TreeNode(id: Int, parentId: Option[Int], label: String, kind: CodeTreeNodeKind, size: Option[Int])

/** Access trait for tree node data.
  *
  * @author robertf
  */
trait TreeNodeDataAccess {
	def foreach(f: TreeNode => Unit): Unit
	def iterate[T](f: Iterator[TreeNode] => T): T

	def getNode(id: Int): Option[TreeNode]

	def getNodeId(signature: String): Option[Int]
	def getNode(signature: String): Option[TreeNode]

	def mapMethodSignature(signature: String, nodeId: Int): Unit
	def mapMethodSignatures(signatures: Iterable[(String, Int)]): Unit = signatures foreach { case (signature, nodeId) => mapMethodSignature(signature, nodeId) }

	def storeNode(node: TreeNode): Unit
	def storeNodes(nodes: Iterable[TreeNode]) = nodes foreach storeNode
}
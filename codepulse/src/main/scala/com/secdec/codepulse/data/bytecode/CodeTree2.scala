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

package com.secdec.codepulse.data.bytecode

import collection.mutable.SortedSet
import scala.math.Ordering

trait CodeTreeNode {
	private var parent: Option[CodeTreeNode] = None
	private val children = SortedSet.empty[CodeTreeNode]

	def id: Int
	def name: String
	def kind: CodeTreeNodeKind
	def size: Option[Int]

	def parentId = parent map { _.id }

	override def equals(that: Any) = that match {
		case that: CodeTreeNode => this.id == that.id
		case _ => false
	}
	override def hashCode = id.hashCode

	def addChild(child: CodeTreeNode) = {
		val didAdd = children.add(child)
		if (child.parent != Some(this)) {
			child.parent.foreach { _.children.remove(child) }
		}
		child.parent = Some(this)
		didAdd
	}

	/* Recursively call the `callback` function on this node and all of its descendants */
	def visitTree(callback: CodeTreeNode => Unit): Unit = {
		callback(this)
		for (child <- children) child.visitTree(callback)
	}

	def findChild(predicate: CodeTreeNode => Boolean): Option[CodeTreeNode] = {
		children.find(predicate)
	}

	def isBranchNode = { children.size > 1 }
	def isLeafNode = { children.isEmpty }
	def isPathNode = { children.size == 1 }

	/** Recursively finds "path" nodes where the node's child has the same `kind` as the node.
	  * For each node in the tree, its children are replaced with 'condensed' versions of themselves.
	  *
	  * In most cases, the condensed version of a node is the node itself. The special case is
	  * when a node has a single child that is the same type; in this case, the condensed version
	  * of the node is its child.
	  *
	  * A path in the tree like `com -> com.secdec -> com.secdec.codepulse` would be replaced by
	  * the `com.secdec.codepulse` node.
	  */
	def condensePathNodes: CodeTreeNode = {
		val childrenCondensed = children.iterator.map(_.condensePathNodes).toList
		children.clear()
		childrenCondensed foreach { _.parent = None }

		childrenCondensed match {
			case child :: Nil if child.kind == this.kind =>
				// just return that child, and this node is effectively removed
				child
			case list =>
				// Note: children is a Set, so if the list suddenly has duplicates, some nodes will be lost.
				// This is counteracted by using a 'parent.child' naming scheme for package and class nodes
				children ++= list
				list foreach { _.parent = Some(this) }
				this
		}
	}

}

object CodeTreeNode {

	implicit def codeTreeNodeOrdering: Ordering[CodeTreeNode] = Ordering.by { node =>
		(weightCriteria(node), node.name, node.size)
	}

	def weightCriteria(node: CodeTreeNode) = node.kind match {
		case CodeTreeNodeKind.Pkg => 0
		case CodeTreeNodeKind.Cls => 1
		case CodeTreeNodeKind.Mth => 2
	}

}

trait CodeTreeNodeFactory {
	def createPackageNode(name: String): CodeTreeNode
	def createClassNode(name: String): CodeTreeNode
	def createMethodNode(name: String, size: Int): CodeTreeNode
}

object CodeTreeNodeFactory {
	private case class PackageNode(id: Int, name: String) extends CodeTreeNode {
		def kind = CodeTreeNodeKind.Pkg
		def size = None
	}
	private case class ClassNode(id: Int, name: String) extends CodeTreeNode {
		def kind = CodeTreeNodeKind.Cls
		def size = None
	}
	private case class MethodNode(id: Int, name: String, methodSize: Int) extends CodeTreeNode {
		def kind = CodeTreeNodeKind.Mth
		def size = Some(methodSize)
	}

	def mkDefaultFactory: CodeTreeNodeFactory = new DefaultImpl

	class DefaultImpl extends CodeTreeNodeFactory {
		val ids = Iterator from 0

		def createPackageNode(name: String): CodeTreeNode = PackageNode(ids.next, name)
		def createClassNode(name: String): CodeTreeNode = ClassNode(ids.next, name)
		def createMethodNode(name: String, size: Int): CodeTreeNode = MethodNode(ids.next, name, size)
	}
}
sealed abstract class CodeTreeNodeKind(val label: String)
object CodeTreeNodeKind {
	case object Pkg extends CodeTreeNodeKind("package")
	case object Cls extends CodeTreeNodeKind("class")
	case object Mth extends CodeTreeNodeKind("method")

	def unapply(label: String) = label match {
		case Pkg.label => Some(Pkg)
		case Cls.label => Some(Cls)
		case Mth.label => Some(Mth)
		case _ => None
	}
}
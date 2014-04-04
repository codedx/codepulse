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

import com.secdec.codepulse.data.MethodTypeParam
import com.secdec.codepulse.data.MethodSignature
import scala.collection.mutable.SortedSet
import CodeForestBuilder._
import java.lang.reflect.Modifier
import com.secdec.codepulse.data.MethodSignatureParser
import com.secdec.codepulse.data.trace.TreeNode

object CodeForestBuilder {

	def main(arts: Array[String]): Unit = {
		val rawSig = "com/avi/codepulse/Cool$Beans.stuff;9;([Ljava/lang/String;)V"
		println(CodePath.parse(rawSig))
	}
}

class CodeForestBuilder {

	private val roots = SortedSet.empty[CodeTreeNode]
	private var nodeFactory = CodeTreeNodeFactory.mkDefaultFactory

	def clear(): this.type = {
		roots.clear()
		nodeFactory = CodeTreeNodeFactory.mkDefaultFactory
		this
	}

	def result = {
		val lb = List.newBuilder[TreeNode]
		for (root <- roots) root.visitTree { node =>
			val id = node.id
			val name = node.name
			val parentId = node.parentId
			val kind = node.kind
			val size = node.size
			lb += TreeNode(id, parentId, name, kind, size)
		}
		lb.result()
	}

	/** Applies the `condensePathNodes` transformation to each root node
	  * currently in the buffer, returning a reference to this builder.
	  */
	def condensePathNodes(): this.type = {
		val rootsSnapshot = roots.toList

		roots.clear()
		val rootsCondensed = rootsSnapshot.map(_.condensePathNodes)
		roots ++= rootsCondensed

		this
	}

	def getOrAddMethod(rawSig: String, size: Int): Option[CodeTreeNode] = {
		MethodSignatureParser.parseSignature(rawSig) map { getOrAddMethod(_, size) }
	}

	def getOrAddMethod(sig: MethodSignature, size: Int): CodeTreeNode = {
		val treePath = CodePath.parse(sig)
		val startNode = addRootPackage(treePath.name)

		def recurse(parent: CodeTreeNode, path: CodePath): CodeTreeNode = path match {
			case CodePath.Package(name, childPath) => recurse(addChildPackage(parent, name), childPath)
			case CodePath.Class(name, childPath) => recurse(addChildClass(parent, name), childPath)
			case CodePath.Method(name) => addChildMethod(parent, name, size)
		}

		recurse(startNode, treePath.child)
	}

	def getOrAddJsp(path: List[String], size: Int): CodeTreeNode = {
		def recurse(parent: CodeTreeNode, path: List[String]): CodeTreeNode = path match {
			case className :: Nil => addChildClass(parent, className)
			case packageNode :: rest => recurse(addChildPackage(parent, packageNode), rest)
		}

		recurse(addRootPackage("JSPs"), path)
	}

	protected def addRootPackage(name: String) = roots.find { node =>
		node.name == name && node.kind == CodeTreeNodeKind.Pkg
	} getOrElse {
		val node = nodeFactory.createPackageNode(name)
		roots.add(node)
		node
	}

	protected def addChildPackage(parent: CodeTreeNode, name: String) = {
		val pkgName = parent.name + '.' + name
		parent.findChild { node =>
			node.name == pkgName && node.kind == CodeTreeNodeKind.Pkg
		} getOrElse {
			val node = nodeFactory.createPackageNode(pkgName)
			parent.addChild(node)
			node
		}
	}

	protected def addChildClass(parent: CodeTreeNode, name: String) = {
		val className =
			if (parent.kind == CodeTreeNodeKind.Cls) parent.name + '.' + name
			else name

		parent.findChild { node =>
			node.name == className && node.kind == CodeTreeNodeKind.Cls
		} getOrElse {
			val node = nodeFactory.createClassNode(className)
			parent.addChild(node)
			node
		}
	}

	protected def addChildMethod(parent: CodeTreeNode, name: String, size: Int) = parent.findChild { node =>
		node.name == name && node.kind == CodeTreeNodeKind.Mth
	} getOrElse {
		val node = nodeFactory.createMethodNode(name, size)
		parent.addChild(node)
		node
	}

}
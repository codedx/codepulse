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
	val JSPGroupName = "JSPs"
}

class CodeForestBuilder(defaultTracedGroups: List[String]) {
	import CodeForestBuilder._

	private val roots = SortedSet.empty[CodeTreeNode]
	private var nodeFactory = CodeTreeNodeFactory.mkDefaultFactory

	def clear(): this.type = {
		roots.clear()
		nodeFactory = CodeTreeNodeFactory.mkDefaultFactory
		this
	}

	def result = {
		val lb = List.newBuilder[TreeNode]
		val tracedGroups = defaultTracedGroups.toSet
		for (root <- roots) root.visitTree { node =>
			val id = node.id
			val name = node.name
			val parentId = node.parentId
			val kind = node.kind
			val size = node.size
			val traced = kind match {
				case CodeTreeNodeKind.Grp | CodeTreeNodeKind.Pkg => Some(tracedGroups contains root.name)
				case _ => None
			}
			lb += TreeNode(id, parentId, name, kind, size, traced)
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

	def getOrAddMethod(group: String, rawSig: String, size: Int): Option[CodeTreeNode] = {
		MethodSignatureParser.parseSignature(rawSig) map { getOrAddMethod(group, _, size) }
	}

	def getOrAddMethod(group: String, sig: MethodSignature, size: Int): CodeTreeNode = {
		val treePath = CodePath.parse(sig)
		val startNode = addGroup(group)

		def recurse(parent: CodeTreeNode, path: CodePath): CodeTreeNode = path match {
			case CodePath.Package(name, childPath) => recurse(addChildPackage(parent, name), childPath)
			case CodePath.Class(name, childPath) => recurse(addChildClass(parent, name), childPath)
			case CodePath.Method(name) => addChildMethod(parent, name, size)
		}

		recurse(startNode, treePath)
	}

	def getOrAddJsp(path: List[String], size: Int): CodeTreeNode = {
		def recurse(parent: CodeTreeNode, path: List[String]): CodeTreeNode = path match {
			case className :: Nil => addChildMethod(parent, className, size)
			case packageNode :: rest => recurse(addChildPackage(parent, packageNode), rest)
		}

		recurse(addGroup(JSPGroupName), path)
	}

	protected def addGroup(name: String) = roots.find { node =>
		node.name == name && node.kind == CodeTreeNodeKind.Grp
	} getOrElse {
		val node = nodeFactory.createGroupNode(name)
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
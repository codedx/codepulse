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
import com.secdec.codepulse.data.model.TreeNodeData

object CodeForestBuilder {
	val JSPGroupName = "JSPs"
}

class CodeForestBuilder {
	import CodeForestBuilder._

	private val roots = SortedSet.empty[CodeTreeNode]
	private var nodeFactory = CodeTreeNodeFactory.mkDefaultFactory
	private val sourceFileIdMap = collection.mutable.Map.empty[(String, String), Int]
	private val sourceFileIds = Iterator from 1

	def clear(): this.type = {
		roots.clear()
		nodeFactory = CodeTreeNodeFactory.mkDefaultFactory
		this
	}

	def result = {
		for {
			root <- roots.toIterator
			node <- root.iterateTree
		} yield {
			val id = node.id
			val name = node.name
			val parentId = node.parentId
			val kind = node.kind
			val size = node.size
			val rootGroup = if (isJspNode(node)) JSPGroupName else getRootGroup(Some(node))
			val sourceFileId = node.sourceFile.flatMap(source => getSourceFileId(rootGroup, source))
			val sourceLocationCount = node.sourceLocationCount
			val methodStartLine = node.methodStartLine
			val isSurfaceMethod = node.isSurfaceMethod
			root -> TreeNodeData(id, parentId, name, kind, size, sourceFileId, sourceLocationCount, methodStartLine, isSurfaceMethod)
		}
	}

	def getSourceFileId(group: String, sourceFile: String): Option[Int] = {
		sourceFileIdMap.get(group, sourceFile) match {
			case Some(v) => Some(v)
			case None => None
		}
	}

	def getRootGroup(node: Option[CodeTreeNode]): String = {
		(for {
			n <- node
		} yield {
			n.kind match {
				case CodeTreeNodeKind.Grp => n.name
				case _ => getRootGroup(n.getParent)
			}
		}) getOrElse ("")
	}

	def sourceFiles = sourceFileIdMap.toMap

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

	def getOrAddMethod(group: String, rawSig: String, size: Int, sourceFile: Option[String], sourceLocationCnt: Option[Int], methodStartLine: Option[Int], isSurfaceMethod: Option[Boolean]): Option[CodeTreeNode] = {
		sourceFile.foreach(addSourceFile(group, _))

		MethodSignatureParser.parseSignature(rawSig, sourceFile) map { getOrAddMethod(group, _, size, sourceFile, sourceLocationCnt, methodStartLine, isSurfaceMethod) }
	}

	def getOrAddMethod(group: String, sig: MethodSignature, size: Int, sourceFile: Option[String], sourceLocationCnt: Option[Int], methodStartLine: Option[Int], isSurfaceMethod: Option[Boolean]): CodeTreeNode = {
		sourceFile.foreach(addSourceFile(group, _))

		val treePath = CodePath.parse(sig)
		val startNode = addRootGroup(group)

		def recurse(parent: CodeTreeNode, path: CodePath): CodeTreeNode = path match {
			case CodePath.Package(name, childPath) => recurse(addChildPackage(parent, name), childPath)
			case CodePath.Class(name, childPath) => recurse(addChildClass(parent, name, sourceFile, sourceLocationCnt), childPath)
			case CodePath.Method(name) => addChildMethod(parent, name, size, sourceFile, sourceLocationCnt, methodStartLine, isSurfaceMethod)
		}

		recurse(startNode, treePath)
	}

	def isJspNode(node: CodeTreeNode): Boolean = {
		node.kind == CodeTreeNodeKind.Mth && node.name.toLowerCase.endsWith(".jsp")
	}

	def getOrAddJsp(path: List[String], size: Int, sourceFile: Option[String], sourceLocationCnt: Option[Int]): CodeTreeNode = {
		sourceFile.foreach(addSourceFile(JSPGroupName, _))

		def recurse(parent: CodeTreeNode, path: List[String]): CodeTreeNode = path match {
			case className :: Nil => addChildMethod(parent, className, size, sourceFile, sourceLocationCnt, None, None)
			case packageNode :: rest => recurse(addFolder(parent, packageNode), rest)
		}

		recurse(addRootGroup(JSPGroupName), path)
	}

	protected def addSourceFile(group: String, sourceFile: String) = {
		sourceFileIdMap.get((group, sourceFile)) match {
			case Some(id) =>
			case None => sourceFileIdMap.put((group, sourceFile), sourceFileIds.next)
		}
	}

	def addExternalSourceFiles(group: String, sourceFiles: List[String]) = {
		sourceFiles.foreach(addSourceFile(group, _))
	}

	protected def addRootGroup(name: String) = {
		val path = (name split '/').toList

		val startRoot = roots.find { node =>
			node.name == path.head && node.kind == CodeTreeNodeKind.Grp
		} getOrElse {
			val node = nodeFactory.createGroupNode(path.head)
			roots.add(node)
			node
		}

		def recurse(parent: CodeTreeNode, path: List[String]): CodeTreeNode = path match {
			case Nil => parent
			case node :: rest => recurse(addFolder(parent, node), rest)
		}

		recurse(startRoot, path.tail)
	}

	protected def addFolder(parent: CodeTreeNode, name: String) = {
		val grpName = parent.name + "/" + name
		parent.findChild { node =>
			node.name == grpName && node.kind == CodeTreeNodeKind.Grp
		} getOrElse {
			val node = nodeFactory.createGroupNode(grpName)
			parent addChild node
			node
		}
	}

	protected def addChildPackage(parent: CodeTreeNode, name: String) = {
		val pkgName = parent.kind match {
			case CodeTreeNodeKind.Grp => name
			case _ => parent.name + '.' + name
		}
		parent.findChild { node =>
			node.name == pkgName && node.kind == CodeTreeNodeKind.Pkg
		} getOrElse {
			val node = nodeFactory.createPackageNode(pkgName)
			parent.addChild(node)
			node
		}
	}

	protected def addChildClass(parent: CodeTreeNode, name: String, sourceFile: Option[String], sourceLocationCnt: Option[Int]) = {
		val className =
			if (parent.kind == CodeTreeNodeKind.Cls) parent.name + '.' + name
			else name

		parent.findChild { node =>
			node.name == className && node.kind == CodeTreeNodeKind.Cls
		} getOrElse {
			val node = nodeFactory.createClassNode(className, sourceFile, sourceLocationCnt)
			parent.addChild(node)
			node
		}
	}

	protected def addChildMethod(parent: CodeTreeNode, name: String, size: Int, sourceFile: Option[String], sourceLocationCnt: Option[Int], methodStartLine: Option[Int], isSurfaceMethod: Option[Boolean]) = parent.findChild { node =>
		node.name == name && node.kind == CodeTreeNodeKind.Mth
	} getOrElse {
		val node = nodeFactory.createMethodNode(name, size, sourceFile, sourceLocationCnt, methodStartLine, isSurfaceMethod)
		parent.addChild(node)
		node
	}

}
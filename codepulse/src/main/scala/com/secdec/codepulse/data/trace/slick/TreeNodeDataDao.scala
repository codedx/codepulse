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

package com.secdec.codepulse.data.trace.slick

import scala.slick.driver.JdbcProfile
import scala.slick.model.ForeignKeyAction
import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.trace._

/** The Slick DAO for tree node data.
  *
  * @author robertf
  */
private[slick] class TreeNodeDataDao(val driver: JdbcProfile) {
	import driver.simple._

	class TreeNodeData(tag: Tag) extends Table[TreeNode](tag, "tree_node_data") {
		private val CodeTreeNodeKindMapping = Map[CodeTreeNodeKind, Char](
			CodeTreeNodeKind.Pkg -> 'p',
			CodeTreeNodeKind.Cls -> 'c',
			CodeTreeNodeKind.Mth -> 'm')
		private val CodeTreeNodeKindUnmapping = CodeTreeNodeKindMapping.map(_.swap)

		private implicit val CodeTreeNodeKindMapper = MappedColumnType.base[CodeTreeNodeKind, Char](
			CodeTreeNodeKindMapping.apply,
			CodeTreeNodeKindUnmapping.apply)

		def id = column[Int]("id", O.PrimaryKey, O.NotNull)
		def parentId = column[Option[Int]]("parent_id")
		def label = column[String]("label", O.NotNull)
		def kind = column[CodeTreeNodeKind]("kind", O.NotNull)
		def * = (id, parentId, label, kind) <> (TreeNode.tupled, TreeNode.unapply)
	}
	val treeNodeData = TableQuery[TreeNodeData]

	class MethodSignatureNodeMap(tag: Tag) extends Table[(String, Int)](tag, "method_signature_node_map") {
		def signature = column[String]("sig", O.PrimaryKey, O.NotNull)
		def nodeId = column[Int]("node_id", O.NotNull)
		def * = signature -> nodeId

		def node = foreignKey("msnm_node", nodeId, treeNodeData)(_.id, onDelete = ForeignKeyAction.Cascade)
	}
	val methodSignatureNodeMap = TableQuery[MethodSignatureNodeMap]

	def create(implicit session: Session) = (treeNodeData.ddl ++ methodSignatureNodeMap.ddl).create

	def get(id: Int)(implicit session: Session): Option[TreeNode] = {
		(for (n <- treeNodeData if n.id === id) yield n).firstOption
	}

	def get(signature: String)(implicit session: Session): Option[TreeNode] = getId(signature).flatMap(get(_))

	def getId(signature: String)(implicit session: Session): Option[Int] = {
		(for (n <- methodSignatureNodeMap if n.signature === signature) yield n.nodeId).firstOption
	}

	def iterateWith[T](f: Iterator[TreeNode] => T)(implicit session: Session): T = {
		val it = treeNodeData.iterator
		try {
			f(it)
		} finally it.close
	}

	def storeMethodSignature(signature: String, node: TreeNode)(implicit session: Session) {
		methodSignatureNodeMap += signature -> node.id
	}

	def storeNode(node: TreeNode)(implicit session: Session) {
		treeNodeData += node
	}
}
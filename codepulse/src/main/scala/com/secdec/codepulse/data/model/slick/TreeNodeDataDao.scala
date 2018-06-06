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

package com.secdec.codepulse.data.model.slick

import scala.slick.driver.JdbcProfile
import scala.slick.model.ForeignKeyAction
import scala.util.Try

import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.model.{TreeNodeData => TreeNode, _}

/** The Slick DAO for tree node data.
  *
  * @author robertf
  */
private[slick] class TreeNodeDataDao(val driver: JdbcProfile, val sourceDataDao: SourceDataDao) extends SlickHelpers {
	import driver.simple._

	class TreeNodeData(tag: Tag) extends Table[TreeNode](tag, "tree_node_data") {
		private val CodeTreeNodeKindMapping = Map[CodeTreeNodeKind, Char](
			CodeTreeNodeKind.Grp -> 'g',
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
		def size = column[Option[Int]]("size", O.Nullable)
		def sourceFileId = column[Option[Int]]("source_file_id", O.Nullable)
		def * = (id, parentId, label, kind, size, sourceFileId) <> (TreeNode.tupled, TreeNode.unapply)
		def labelIndex = index("tnd_label_index", label)

		def sourceFile = foreignKey("tree_node_data_to_source_file", sourceFileId, sourceDataDao.sourceFilesQuery)(_.id, onDelete = ForeignKeyAction.Cascade)
	}
	val treeNodeData = TableQuery[TreeNodeData]

	class TracedNodes(tag: Tag) extends Table[(Int, Boolean)](tag, "traced_nodes") {
		def nodeId = column[Int]("node_id", O.PrimaryKey, O.NotNull)
		def traced = column[Boolean]("traced", O.NotNull)
		def * = (nodeId, traced)

		def node = foreignKey("tn_node", nodeId, treeNodeData)(_.id, onDelete = ForeignKeyAction.Cascade)
	}
	val tracedNodes = TableQuery[TracedNodes]

	class TreeNodeFlags(tag: Tag) extends Table[(Int, TreeNodeFlag)](tag, "tree_node_flags") {
		private val TreeNodeFlagMapping = Map[TreeNodeFlag, String](
			TreeNodeFlag.HasVulnerability -> "has_vuln")
		private val TreeNodeFlagUnmapping = TreeNodeFlagMapping.map(_.swap)

		private implicit val TreeNodeFlagMapper = MappedColumnType.base[TreeNodeFlag, String](
			TreeNodeFlagMapping.apply,
			TreeNodeFlagUnmapping.apply)

		def nodeId = column[Int]("node_id", O.PrimaryKey, O.NotNull)
		def flag = column[TreeNodeFlag]("flag", O.NotNull)
		def * = (nodeId, flag)
	}
	val treeNodeFlags = TableQuery[TreeNodeFlags]

	class MethodSignatureNodeMap(tag: Tag) extends Table[MethodSignatureNode](tag, "method_signature_node_map") {
		def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
		def signature = column[String]("sig", O.NotNull)
		def nodeId = column[Int]("node_id", O.NotNull)
		def * = (id, signature, nodeId) <> (MethodSignatureNode.tupled, MethodSignatureNode.unapply)

		def node = foreignKey("msnm_node", nodeId, treeNodeData)(_.id, onDelete = ForeignKeyAction.Cascade)
	}
	val methodSignatureNodeMap = TableQuery[MethodSignatureNodeMap]

	class JspNodeMap(tag: Tag) extends Table[(String, Int)](tag, "jsp_node_map") {
		def jspClass = column[String]("jsp", O.PrimaryKey, O.NotNull)
		def nodeId = column[Int]("node_id", O.NotNull)
		def * = jspClass -> nodeId

		def node = foreignKey("jspnm_node", nodeId, treeNodeData)(_.id, onDelete = ForeignKeyAction.Cascade)
	}
	val jspNodeMap = TableQuery[JspNodeMap]

	def create(implicit session: Session) = (treeNodeData.ddl ++ tracedNodes.ddl ++ treeNodeFlags.ddl ++ methodSignatureNodeMap.ddl ++ jspNodeMap.ddl).create

	def get(id: Int)(implicit session: Session): Option[TreeNode] = {
		(for (n <- treeNodeData if n.id === id) yield n).firstOption
	}

	def get(label: String)(implicit session: Session): Option[TreeNode] = {
		(for (n <- treeNodeData if n.label === label) yield n).firstOption
	}

	def getForSignature(signature: String)(implicit session: Session): List[TreeNode] = getIdsForSignature(signature).flatMap(get(_))

	def getForJsp(jspClass: String)(implicit session: Session): Option[TreeNode] = getIdForJsp(jspClass).flatMap(get(_))

	def getIdsForSignature(signature: String)(implicit session: Session): List[Int] = {
		(for (n <- methodSignatureNodeMap if n.signature === signature) yield n.nodeId).list
	}

	def getIdForJsp(jspClass: String)(implicit session: Session): Option[Int] = {
		(for (n <- jspNodeMap if n.jspClass === jspClass) yield n.nodeId).firstOption
	}

	def iterateWith[T](f: Iterator[TreeNode] => T)(implicit session: Session): T = {
		val it = treeNodeData.iterator
		try {
			f(it)
		} finally it.close
	}

	def iterateMethodMappingsWith[T](f: Iterator[MethodSignatureNode] => T)(implicit session: Session): T = {
		val it = methodSignatureNodeMap.iterator
		try {
			f(it)
		} finally it.close
	}

	def iterateJspMappingsWith[T](f: Iterator[(String, Int)] => T)(implicit session: Session): T = {
		val it = jspNodeMap.iterator
		try {
			f(it)
		} finally it.close
	}

	def storeMethodSignature(methodSignatureNode: MethodSignatureNode)(implicit session: Session) {
		methodSignatureNodeMap += methodSignatureNode
	}

	def storeMethodSignatures(signatures: Iterable[MethodSignatureNode])(implicit session: Session) {
		fastImport { methodSignatureNodeMap ++= signatures }
	}

	def storeJsp(jspPath: String, nodeId: Int)(implicit session: Session) {
		jspNodeMap += jspPath -> nodeId
	}

	def storeJsps(jsps: Iterable[(String, Int)])(implicit session: Session) {
		fastImport { jspNodeMap ++= jsps }
	}

	def storeNode(node: TreeNode)(implicit session: Session) {
		treeNodeData += node
	}

	def storeNodes(nodes: Iterable[TreeNode])(implicit session: Session) {
		fastImport { treeNodeData ++= nodes }
	}

	def getTraced()(implicit session: Session) = tracedNodes.list

	def storeTracedValues(values: Iterable[(Int, Option[Boolean])])(implicit session: Session) {
		fastImport {
			tracedNodes ++= values flatMap {
				case (id, Some(traced)) => Some(id -> traced)
				case _ => None
			}
		}
	}

	def updateTraced(id: Int, traced: Option[Boolean])(implicit session: Session) {
		traced match {
			case Some(traced) =>
				val q = for (row <- tracedNodes if row.nodeId === id) yield row.traced
				q.update(traced) match {
					case 0 => tracedNodes += id -> traced
					case _ =>
				}

			case None =>
				val q = for (row <- tracedNodes if row.nodeId === id) yield row
				q.delete
		}
	}

	def getFlags(id: Int)(implicit session: Session): List[TreeNodeFlag] = {
		// wrapped in a try, since older versions may not have the concept of flags
		Try {
			(for (flag <- treeNodeFlags if flag.nodeId === id) yield flag.flag).list
		} getOrElse Nil
	}

	def setFlag(id: Int, flag: TreeNodeFlag)(implicit session: Session) {
		treeNodeFlags += id -> flag
	}

	def clearFlag(id: Int, flag: TreeNodeFlag)(implicit session: Session) {
		(for (flag <- treeNodeFlags if flag.nodeId === id) yield flag).delete
	}
}
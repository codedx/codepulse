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

import scala.slick.jdbc.JdbcBackend.Database
import com.secdec.codepulse.data.model.{MethodSignatureNode, TreeNodeData, TreeNodeDataAccess, TreeNodeFlag}

/** Slick-based TreeNodeDataAccess implementation.
  *
  * @author robertf
  */
private[slick] class SlickTreeNodeDataAccess(dao: TreeNodeDataDao, db: Database) extends TreeNodeDataAccess {
	def foreach(f: TreeNodeData => Unit) {
		iterate { _.foreach(f) }
	}

	def iterate[T](f: Iterator[TreeNodeData] => T): T = {
		db withSession { implicit session =>
			dao.iterateWith(f)
		}
	}

	def getNode(id: Int): Option[TreeNodeData] = db withSession { implicit session =>
		dao get id
	}

	def getNode(label: String): Option[TreeNodeData] = db withSession { implicit session =>
		dao get label
	}

	def getNodesForSignature(sig: String): List[TreeNodeData] = db withSession { implicit session =>
		dao getForSignature sig
	}

	def getNodeIdsForSignature(sig: String): List[Int] = db withSession { implicit session =>
		dao getIdsForSignature sig
	}

	def foreachMethodMapping(f: MethodSignatureNode => Unit) {
		iterateMethodMappings { _.foreach(f) }
	}

	def iterateMethodMappings[T](f: Iterator[MethodSignatureNode] => T): T = {
		db withSession { implicit session =>
			dao.iterateMethodMappingsWith(f)
		}
	}

	def mapMethodSignature(methodSignatureNode: MethodSignatureNode) = db withTransaction { implicit transaction =>
		dao.storeMethodSignature(methodSignatureNode)
	}

	override def mapMethodSignatures(signatures: Iterable[MethodSignatureNode]) = db withTransaction { implicit transaction =>
		dao storeMethodSignatures signatures
	}

	def getNodeForJsp(jspPath: String): Option[TreeNodeData] = db withSession { implicit session =>
		dao getForJsp jspPath
	}

	def getNodeIdForJsp(jspPath: String): Option[Int] = db withSession { implicit session =>
		dao getIdForJsp jspPath
	}

	def foreachJspMapping(f: (String, Int) => Unit) {
		iterateJspMappings { _.foreach { case (method, nodeId) => f(method, nodeId) } }
	}

	def iterateJspMappings[T](f: Iterator[(String, Int)] => T): T = {
		db withSession { implicit session =>
			dao iterateJspMappingsWith f
		}
	}

	def mapJsp(jspClass: String, nodeId: Int) = db withTransaction { implicit transaction =>
		dao.storeJsp(jspClass, nodeId)
	}

	override def mapJsps(jsps: Iterable[(String, Int)]) = db withTransaction { implicit transaction =>
		dao storeJsps jsps
	}

	def bulkImport(data: Iterable[BulkImportElement]) = db withTransaction { implicit transaction =>
		dao storeNodes data.map(_.data)
		dao storeTracedValues data.map { element => element.data.id -> element.traced }
	}

	def storeNode(node: TreeNodeData) = db withTransaction { implicit transaction =>
		dao storeNode node
	}

	override def storeNodes(nodes: Iterable[TreeNodeData]) = db withTransaction { implicit transaction =>
		dao storeNodes nodes
	}

	private lazy val tracedCache = collection.mutable.Map(db withSession { implicit session => dao.getTraced }: _*)

	def isTraced(id: Int): Option[Boolean] = tracedCache.get(id)

	def updateTraced(id: Int, traced: Option[Boolean]) = {
		db withTransaction { implicit transaction =>
			dao.updateTraced(id, traced)
		}

		traced match {
			case Some(traced) => tracedCache += id -> traced
			case None => tracedCache -= id
		}
	}

	def updateSourceLocationCount(id: Int, sourceLocationCount: Int) = {
		db withTransaction { implicit transaction =>
			dao.updateSourceLocationCount(id, sourceLocationCount)
		}
	}

	private lazy val flagCache = collection.mutable.Map.empty[Int, List[TreeNodeFlag]]

	def getFlags(id: Int): List[TreeNodeFlag] = flagCache.getOrElse(id, { db withSession { implicit session =>
		dao.getFlags(id)
	}})

	def setFlag(id: Int, flag: TreeNodeFlag) {
		db withTransaction { implicit transaction =>
			dao.setFlag(id, flag)
			flagCache remove id
		}
	}

	def clearFlag(id: Int, flag: TreeNodeFlag) {
		db withTransaction { implicit transaction =>
			dao.clearFlag(id, flag)
			flagCache remove id
		}
	}

	def findMethods(sourceFilePath: String): List[Int] = db withSession { implicit session =>
		dao findMethods(sourceFilePath)
	}

	def findMethods(sourceFilePath: String, startingLineNumber: Int, endingLineNumber: Int): List[Int] = db withSession { implicit session =>
		dao findMethods(sourceFilePath, startingLineNumber, endingLineNumber)
	}

	def markSurfaceMethod(id: Int): Unit = {
		db withTransaction { implicit transaction =>
			dao.markSurfaceMethod(id)
		}
	}

	def getSurfaceMethodAncestorPackages: List[Int] = db.withSession { implicit session =>
		dao.getSurfaceMethodAncestorPackages
	}
}
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

import scala.slick.jdbc.JdbcBackend.Database
import com.secdec.codepulse.data.trace.{ TreeNode, TreeNodeDataAccess }

/** Slick-based TreeNodeDataAccess implementation.
  *
  * @author robertf
  */
private[slick] class SlickTreeNodeDataAccess(dao: TreeNodeDataDao, db: Database) extends TreeNodeDataAccess {
	def iterateNodes(f: TreeNode => Unit) {
		db withSession { implicit session =>
			dao.iterateWith { iterator =>
				iterator.foreach(f)
			}
		}
	}

	def getNode(id: Int): Option[TreeNode] = db withSession { implicit session =>
		dao get id
	}

	def getNode(sig: String): Option[TreeNode] = db withSession { implicit session =>
		dao get sig
	}

	def getNodeId(sig: String): Option[Int] = db withSession { implicit session =>
		dao getId sig
	}

	def mapMethodSignature(sig: String, node: TreeNode) = db withTransaction { implicit transaction =>
		dao.storeMethodSignature(sig, node)
	}

	def storeNode(node: TreeNode) = db withTransaction { implicit transaction =>
		dao storeNode node
	}
}
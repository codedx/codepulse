package com.secdec.codepulse.data.model.slick

import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.model.TreeNodeData
import org.scalatest.{BeforeAndAfter, FunSpec}

import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.Database
import scala.slick.jdbc.{JdbcBackend, StaticQuery => Q}

class TreeNodeDataDaoSuite extends FunSpec with BeforeAndAfter {

  var projectDb: JdbcBackend.DatabaseDef = null
  var sourceDataDao: SourceDataDao = null
  var treeNodeDataDao: TreeNodeDataDao = null

  describe("When one package, class, and surface method") {
    it("should return package ID") {
      projectDb.withSession {
        implicit session => {
          treeNodeDataDao.storeNode(TreeNodeData(1, None, "Package", CodeTreeNodeKind.Pkg, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(2, Some(1), "Class", CodeTreeNodeKind.Cls, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(3, Some(2), "Surface Method", CodeTreeNodeKind.Mth, None, None, None, None, Some(true)))
          treeNodeDataDao.storeNode(TreeNodeData(4, Some(2), "Regular Method", CodeTreeNodeKind.Mth, None, None, None, None, Some(false)))

          val methodList = treeNodeDataDao.getSurfaceMethodAncestorPackages
          assert(methodList.length == 1)
          assert(methodList.head == 1)
        }
      }
    }
  }

  describe("When one package, class, and no surface methods") {
    it("should return an empty list") {
      projectDb.withSession {
        implicit session => {
          treeNodeDataDao.storeNode(TreeNodeData(1, None, "Package", CodeTreeNodeKind.Pkg, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(2, Some(1), "Class", CodeTreeNodeKind.Cls, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(3, Some(2), "Regular Method 1", CodeTreeNodeKind.Mth, None, None, None, None, Some(false)))
          treeNodeDataDao.storeNode(TreeNodeData(4, Some(2), "Regular Method 2", CodeTreeNodeKind.Mth, None, None, None, None, Some(false)))

          val methodList = treeNodeDataDao.getSurfaceMethodAncestorPackages
          assert(methodList.length == 0)
        }
      }
    }
  }

  describe("When one nested package, class, and a surface method") {
    it("should return nested package ID") {
      projectDb.withSession {
        implicit session => {
          treeNodeDataDao.storeNode(TreeNodeData(1, None, "Package", CodeTreeNodeKind.Pkg, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(2, Some(1), "Nested Package", CodeTreeNodeKind.Pkg, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(3, Some(2), "Class", CodeTreeNodeKind.Cls, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(4, Some(3), "Surface Method", CodeTreeNodeKind.Mth, None, None, None, None, Some(true)))
          treeNodeDataDao.storeNode(TreeNodeData(5, Some(3), "Regular Method", CodeTreeNodeKind.Mth, None, None, None, None, Some(false)))

          val methodList = treeNodeDataDao.getSurfaceMethodAncestorPackages
          assert(methodList.length == 1)
          assert(methodList.head == 2)
        }
      }
    }
  }

  describe("When one nested package, nested class, and a surface method") {
    it("should return nested package ID") {
      projectDb.withSession {
        implicit session => {
          treeNodeDataDao.storeNode(TreeNodeData(1, None, "Package", CodeTreeNodeKind.Pkg, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(2, Some(1), "Nested Package", CodeTreeNodeKind.Pkg, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(3, Some(2), "Class", CodeTreeNodeKind.Cls, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(4, Some(3), "Nested Class", CodeTreeNodeKind.Cls, None, None, None, None, None))
          treeNodeDataDao.storeNode(TreeNodeData(5, Some(4), "Surface Method", CodeTreeNodeKind.Mth, None, None, None, None, Some(true)))
          treeNodeDataDao.storeNode(TreeNodeData(6, Some(3), "Regular Method", CodeTreeNodeKind.Mth, None, None, None, None, Some(false)))

          val methodList = treeNodeDataDao.getSurfaceMethodAncestorPackages
          assert(methodList.length == 1)
          assert(methodList.head == 2)
        }
      }
    }
  }

  before {
    def makeDb(db: JdbcBackend.DatabaseDef, dbName: String): JdbcBackend.DatabaseDef = {
      if (db == null) {
        return Database.forURL(s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
      } else {
        db.withSession(implicit x => Q.updateNA("drop all objects").execute())
        return db
      }
    }

    projectDb = makeDb(projectDb, "project-for-export")

    sourceDataDao = new SourceDataDao(H2Driver)
    treeNodeDataDao = new TreeNodeDataDao(H2Driver, sourceDataDao)

    projectDb.withSession {
      implicit session => {
        sourceDataDao.create
        treeNodeDataDao.create
      }
    }
  }
}

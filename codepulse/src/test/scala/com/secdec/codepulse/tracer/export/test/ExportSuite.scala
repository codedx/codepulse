/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */
package com.secdec.codepulse.tracer.export.test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileNotFoundException}
import java.util.zip.ZipInputStream

import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.model.{MethodSignatureNode, ProjectId, TreeNodeData}
import com.secdec.codepulse.data.model.slick.{ProjectMetadataDao, SlickProjectData, SlickProjectMetadataMaster}
import com.secdec.codepulse.tracer.ProjectManager
import com.secdec.codepulse.tracer.export.ProjectExporter
import org.scalatest.{BeforeAndAfter, FunSpec}

import scala.concurrent.duration._
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend
import scala.slick.jdbc.JdbcBackend.Database
import scala.slick.jdbc.{StaticQuery => Q}

class ExportSuite extends FunSpec with BeforeAndAfter {

  var data: SlickProjectData = null
  var projectsDb: JdbcBackend.DatabaseDef = null
  var projectDb: JdbcBackend.DatabaseDef = null

  describe("Manifest of exported project") {
    it("should specify version 2") {
      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get(".manifest").get.contains("version=2"))
    }
  }

  describe("Project file of exported project") {
    it("should specify name project1") {
      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("project.json").get.contains("\"name\" : \"project1\""))
    }
  }

  describe("Source files of exported project") {
    it("should include path and id") {
      data.sourceData.importSourceFiles(Map[Int,String]((10, "C:\\code\\program.java")))

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("sourceFiles.json").get == "[ {\r\n  \"id\" : 10,\r\n  \"path\" : \"C:\\\\code\\\\program.java\"\r\n} ]")
    }
  }

  describe("Source locations of exported project") {
    it("should handle optional start/end character") {
      data.sourceData.importSourceFiles(Map[Int,String]((10, "C:\\code\\program.java")))
      data.sourceData.getSourceLocationId(10, 1, 1, Option(1), Option(5))
      data.sourceData.getSourceLocationId(10, 2, 2, None, None)

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("sourceLocations.json").get == "[ {\r\n  \"id\" : 1,\r\n  \"sourceFileId\" : 10,\r\n  \"startLine\" : 1,\r\n  \"endLine\" : 1,\r\n  \"startCharacter\" : 1,\r\n  \"endCharacter\" : 5\r\n}, {\r\n  \"id\" : 2,\r\n  \"sourceFileId\" : 10,\r\n  \"startLine\" : 2,\r\n  \"endLine\" : 2\r\n} ]")
    }
  }

  describe("Method mappings of exported project") {
    it("should include signature and node") {
      data.treeNodeData.storeNode(TreeNodeData(1, None, "method1", CodeTreeNodeKind.Mth, Option(50), None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "method1", 1))

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("method-mappings.json").get == "[ {\r\n  \"signature\" : \"method1\",\r\n  \"node\" : 1\r\n} ]")
    }
  }

  describe("Recordings of exported project") {
    it("should include recording metadata") {
      val recording = data.recordings.create()
      recording.clientLabel = Option("Recording 1")
      recording.clientColor = Option("#FFFFFF")

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(recording.id == 1)
      assert(contents.get("recordings.json").get == "[ {\r\n  \"id\" : 1,\r\n  \"running\" : true,\r\n  \"label\" : \"Recording 1\",\r\n  \"color\" : \"#FFFFFF\"\r\n} ]")
    }
  }

  describe("Nodes of exported project") {
    it("should not reference source file when source file is unavailable") {
      data.treeNodeData.storeNode(TreeNodeData(1, None, "method1", CodeTreeNodeKind.Mth, Option(50), None))

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("nodes.json").get == "[ {\r\n  \"id\" : 1,\r\n  \"label\" : \"method1\",\r\n  \"kind\" : \"method\",\r\n  \"size\" : 50\r\n} ]")
    }

    it("should reference source file when source file is unavailable") {
      data.sourceData.importSourceFiles(Map[Int,String]((10, "C:\\code\\program.java")))
      data.treeNodeData.storeNode(TreeNodeData(5, None, "method1", CodeTreeNodeKind.Mth, Option(50), Option(10)))

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("nodes.json").get == "[ {\r\n  \"id\" : 5,\r\n  \"label\" : \"method1\",\r\n  \"kind\" : \"method\",\r\n  \"size\" : 50,\r\n  \"sourceFileId\" : 10\r\n} ]")
    }
  }

  describe("Encounters of exported project unassociated with a recorcing") {
    it("should not reference source location when it is unavailable") {
      data.treeNodeData.storeNode(TreeNodeData(5, None, "method1", CodeTreeNodeKind.Mth, Option(50), None))
      data.encounters.record(Nil, List[(Int,Option[Int])](5 -> None))
      data.flush()

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("encounters.json").get == "{\r\n  \"all\" : [ {\r\n    \"nodeId\" : 5\r\n  } ]\r\n}")
    }

   it("should reference source location when it is available") {
     data.sourceData.importSourceFiles(Map[Int,String]((10, "C:\\code\\program.java")))
     data.treeNodeData.storeNode(TreeNodeData(5, None, "method1", CodeTreeNodeKind.Mth, Option(50), Option(10)))
     val sourceLocationId = data.sourceData.getSourceLocationId(10, 1, 1, Option(1), Option(5))
     data.encounters.record(Nil, List[(Int,Option[Int])](5 -> Option(sourceLocationId)))
     data.flush()

     val outputStream = new ByteArrayOutputStream()
     ProjectExporter.exportTo(outputStream, data)
     val contents = unzipExport(outputStream)
     assert(contents.get("encounters.json").get == "{\r\n  \"all\" : [ {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 1\r\n  } ]\r\n}")
    }
  }

  describe("Encounters of exported project associated with a recording") {
    it("should not reference source location when it is unavailable") {
      val recording = data.recordings.create()
      recording.clientLabel = Option("Recording 1")
      recording.clientColor = Option("#FFFFFF")

      data.treeNodeData.storeNode(TreeNodeData(5, None, "method1", CodeTreeNodeKind.Mth, Option(50), None))
      data.encounters.record(List[Int](recording.id), List[(Int,Option[Int])](5 -> None))
      data.flush()
      data.encounters.record(List[Int](recording.id), List[(Int,Option[Int])](5 -> None))
      data.flush()

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("encounters.json").get == "{\r\n  \"all\" : [ {\r\n    \"nodeId\" : 5\r\n  } ],\r\n  \"1\" : [ {\r\n    \"nodeId\" : 5\r\n  } ]\r\n}")
    }

    it("should reference source location when it is available") {
      val recording = data.recordings.create()
      recording.clientLabel = Option("Recording 1")
      recording.clientColor = Option("#FFFFFF")

      data.sourceData.importSourceFiles(Map[Int,String]((10, "C:\\code\\program.java")))
      data.treeNodeData.storeNode(TreeNodeData(5, None, "method1", CodeTreeNodeKind.Mth, Option(50), Option(10)))
      val sourceLocationId1 = data.sourceData.getSourceLocationId(10, 1, 1, Option(1), Option(5))
      val sourceLocationId2 = data.sourceData.getSourceLocationId(10, 5, 5, None, None)
      data.encounters.record(Nil, List[(Int,Option[Int])](5 -> Option(sourceLocationId1)))
      data.flush()
      data.encounters.record(List[Int](recording.id), List[(Int,Option[Int])](5 -> Option(sourceLocationId1), 5 -> Option(sourceLocationId2)))
      data.flush()

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("encounters.json").get == "{\r\n  \"all\" : [ {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 1\r\n  }, {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 2\r\n  } ],\r\n  \"1\" : [ {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 2\r\n  }, {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 1\r\n  } ]\r\n}")
    }
  }

  describe("Encounters of exported project associated with multiple recordings") {
    it("should include all encounters") {
      val recording1 = data.recordings.create()
      recording1.clientLabel = Option("Recording 1")
      recording1.clientColor = Option("#FFFFFF")

      val recording2 = data.recordings.create()
      recording2.clientLabel = Option("Recording 2")
      recording2.clientColor = Option("#000000")

      data.sourceData.importSourceFiles(Map[Int,String]((10, "C:\\code\\program.java")))
      data.sourceData.importSourceFiles(Map[Int,String]((11, "C:\\code\\foo.java")))
      data.treeNodeData.storeNode(TreeNodeData(5, None, "method1", CodeTreeNodeKind.Mth, Option(50), Option(10)))
      data.treeNodeData.storeNode(TreeNodeData(6, None, "method1", CodeTreeNodeKind.Mth, Option(50), Option(11)))

      val sourceLocationId1 = data.sourceData.getSourceLocationId(10, 1, 1, Option(1), Option(5))
      val sourceLocationId2 = data.sourceData.getSourceLocationId(10, 5, 5, None, None)
      val sourceLocationId3 = data.sourceData.getSourceLocationId(11, 2, 3, None, None)

      data.encounters.record(Nil, List[(Int,Option[Int])](5 -> None))
      data.flush()
      data.encounters.record(Nil, List[(Int,Option[Int])](5 -> Option(sourceLocationId1)))
      data.flush()
      data.encounters.record(List[Int](recording1.id), List[(Int,Option[Int])](5 -> Option(sourceLocationId1)))
      data.flush()
      data.encounters.record(List[Int](recording1.id), List[(Int,Option[Int])](5 -> Option(sourceLocationId2)))
      data.flush()
      data.encounters.record(List[Int](recording2.id), List[(Int,Option[Int])](6 -> Option(sourceLocationId3)))
      data.flush()

      val outputStream = new ByteArrayOutputStream()
      ProjectExporter.exportTo(outputStream, data)
      val contents = unzipExport(outputStream)
      assert(contents.get("encounters.json").get == "{\r\n  \"all\" : [ {\r\n    \"nodeId\" : 5\r\n  }, {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 1\r\n  }, {\r\n    \"nodeId\" : 6,\r\n    \"sourceLocationId\" : 3\r\n  }, {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 2\r\n  } ],\r\n  \"1\" : [ {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 2\r\n  }, {\r\n    \"nodeId\" : 5,\r\n    \"sourceLocationId\" : 1\r\n  } ],\r\n  \"2\" : [ {\r\n    \"nodeId\" : 6,\r\n    \"sourceLocationId\" : 3\r\n  } ]\r\n}")
    }
  }

  describe("Project containing input file path") {
    it("should try to export file path") {
      projectsDb.withSession {
        implicit session =>
          projectMetadataDao.set(1, "input", "invalidDrive:\\file")
      }
      val outputStream = new ByteArrayOutputStream()
      val caught = intercept[FileNotFoundException] {
        ProjectExporter.exportTo(outputStream, data)
      }
      assert(caught.getMessage() == "invalidDrive:\\file (The filename, directory name, or volume label syntax is incorrect)")
    }
  }

  var projectMetadataDao:ProjectMetadataDao = null

  before {
    def makeDb(db: JdbcBackend.DatabaseDef, dbName: String): JdbcBackend.DatabaseDef = {
      if (db == null) {
        return Database.forURL(s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
      } else {
        db.withSession(implicit x => Q.updateNA("drop all objects").execute())
        return db
      }
    }

    projectsDb = makeDb(projectsDb, "projects-for-export")
    projectDb = makeDb(projectDb, "project-for-export")

    projectMetadataDao = new ProjectMetadataDao(H2Driver)

    val projectId = ProjectId(1)
    projectsDb.withSession {
      implicit session =>
        projectMetadataDao.create
        projectMetadataDao.set(projectId.num, "name", "project1")
        projectMetadataDao.set(projectId.num, "hasCustomName", "true")
        projectMetadataDao.set(projectId.num, "importDate", "1525791373726")
    }

    val slickProjectMetadataMaster = new SlickProjectMetadataMaster(projectMetadataDao, projectsDb)
    data = new SlickProjectData(projectId, projectDb, H2Driver, slickProjectMetadataMaster.get(projectId.num), 500, 1.second, ProjectManager.defaultActorSystem)
    data.init()
  }

  def unzipExport(outputStream: ByteArrayOutputStream): Map[String, String] = {
    val zipContents = collection.mutable.Map.empty[String,String]
    val zipStream = new ZipInputStream(new ByteArrayInputStream(outputStream.toByteArray()))
    var entry = zipStream.getNextEntry
    while (entry != null) {
      val entryData = Stream.continually(zipStream.read()).takeWhile(x => x != -1).map(_.toByte).toArray
      zipContents.put(entry.getName, new String(entryData))
      entry = zipStream.getNextEntry
    }
    zipContents.toMap
  }
}

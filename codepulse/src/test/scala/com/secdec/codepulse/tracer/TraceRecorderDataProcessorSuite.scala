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

package com.secdec.codepulse.tracer

import com.codedx.codepulse.hq.protocol.DataMessageContent
import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.model.{MethodSignatureNode, ProjectId, TreeNodeData}
import com.secdec.codepulse.data.model.slick.{ProjectMetadataDao, SlickProjectData, SlickProjectMetadataMaster}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}

import scala.concurrent.duration._
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend
import scala.slick.jdbc.JdbcBackend.Database
import scala.slick.jdbc.{StaticQuery => Q}

class TraceRecorderDataProcessorSuite extends FunSpec with Matchers with BeforeAndAfter with MockFactory {

  var data: SlickProjectData = null
  var projectsDb: JdbcBackend.DatabaseDef = null
  var projectDb: JdbcBackend.DatabaseDef = null

  describe("MethodEntry sent after MapSourceLocation") {
    it("should not defer processing") {
      val methodId = 20
      val clientMethodId = 1
      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), None, None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))
      assert(recorder.deferredMethodEntries.isEmpty, "Expected no deferred entries")

      recorder.processMessage(DataMessageContent.MethodEntry(clientMethodId, 0, 0))
      assert(recorder.deferredMethodEntries.isEmpty, "Expected no deferred entries")

      data.flush(); Thread.sleep(2500)
    }
  }

  describe("MethodVisit sent after MapMethodSignature and MapSourceLocation") {
    it("should not defer processing") {
      val methodId = 21
      val methodSourceFile = 22
      val clientMethodId = 2
      val clientMethodSourceLocationId = 3

      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.sourceData.importSourceFiles(Map(methodSourceFile -> "C:\\source.cs"))
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), Option(methodSourceFile), None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))

      recorder.processMessage(DataMessageContent.MapSourceLocation(clientMethodId, 1, 1, 1, 1, clientMethodSourceLocationId))
      assert(recorder.deferredMapSourceLocations.isEmpty, "Expected no deferred entries")

      recorder.processMessage(DataMessageContent.MethodVisit(methodId, clientMethodSourceLocationId, 0, 0))
      assert(recorder.deferredMethodVisits.isEmpty, "Expected no deferred entries")
      data.flush(); Thread.sleep(2500)
    }
  }

  describe("MethodEntry sent before MapSourceLocation") {
    it("should defer processing") {
      val methodId = 23
      val clientMethodId = 4
      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), None, None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MethodEntry(clientMethodId, 0, 0))
      assert(recorder.deferredMethodEntries.contains(clientMethodId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))
      assert(recorder.deferredMethodEntries.isEmpty, "Expected no deferred entries")
      data.flush(); Thread.sleep(2500)
    }
  }

  describe("SourceLocationCount sent before MapSourceLocation") {
    it("should defer processing") {
      val methodId = 35
      val clientMethodId = 14
      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), None, None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.SourceLocationCount(clientMethodId, 20))
      assert(recorder.deferredSourceLocationCounts.contains(clientMethodId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))
      assert(recorder.deferredSourceLocationCounts.isEmpty, "Expected no deferred entries")
      data.flush(); Thread.sleep(2500)
    }
  }

  describe("MethodVisit, no source file, sent before MapSourceLocation") {
    it("should defer processing") {
      val methodId = 24
      val clientMethodId = 5
      val clientMethodSourceLocationId = 6

      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), None, None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))

      recorder.processMessage(DataMessageContent.MethodVisit(methodId, clientMethodSourceLocationId, 0, 0))
      assert(recorder.deferredMethodVisits.contains(clientMethodSourceLocationId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MapSourceLocation(clientMethodId, 1, 1, 1, 1, clientMethodSourceLocationId))
      assert(recorder.deferredMethodVisits.isEmpty, "Expected no deferred entries")
      data.flush(); Thread.sleep(2500)
    }
  }

  describe("MethodVisit, with source file, sent before MapSourceLocation") {
    it("should defer processing") {
      val methodId = 25
      val methodSourceFile = 26
      val clientMethodId = 7
      val clientMethodSourceLocationId = 8

      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.sourceData.importSourceFiles(Map(methodSourceFile -> "C:\\source.cs"))
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), Option(methodSourceFile), None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))

      recorder.processMessage(DataMessageContent.MethodVisit(methodId, clientMethodSourceLocationId, 0, 0))
      assert(recorder.deferredMethodVisits.contains(clientMethodSourceLocationId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MapSourceLocation(clientMethodId, 1, 1, 1, 1, clientMethodSourceLocationId))
      assert(recorder.deferredMethodVisits.isEmpty, "Expected no deferred entries")
      data.flush(); Thread.sleep(2500)
    }
  }

  describe("MethodVisit sent before MapSourceLocation and MapMethodSignature") {
    it("should defer processing") {
      val methodId = 27
      val methodSourceFile = 28
      val clientMethodId = 9
      val clientMethodSourceLocationId = 10

      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.sourceData.importSourceFiles(Map(methodSourceFile -> "C:\\source.cs"))
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), Option(methodSourceFile), None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MethodVisit(methodId, clientMethodSourceLocationId, 0, 0))
      assert(recorder.deferredMethodVisits.contains(clientMethodSourceLocationId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MapSourceLocation(clientMethodId, 1, 1, 1, 1, clientMethodSourceLocationId))
      assert(recorder.deferredMapSourceLocations.contains(clientMethodId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))
      assert(recorder.deferredMethodVisits.isEmpty, "Expected no deferred entries")
      assert(recorder.deferredMapSourceLocations.isEmpty, "Expected no deferred entries")
      data.flush(); Thread.sleep(2500)
    }
  }

  describe("MethodVisit sent before MapMethodSignature") {
    it("should defer processing") {
      val methodId = 29
      val methodSourceFile = 30
      val clientMethodId = 11
      val clientMethodSourceLocationId = 12

      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.sourceData.importSourceFiles(Map(methodSourceFile -> "C:\\source.cs"))
      data.treeNodeData.storeNode(new TreeNodeData(methodId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), Option(methodSourceFile), None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MapSourceLocation(clientMethodId, 1, 1, 1, 1, clientMethodSourceLocationId))
      assert(recorder.deferredMapSourceLocations.contains(clientMethodId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MethodVisit(methodId, clientMethodSourceLocationId, 0, 0))
      assert(recorder.deferredMethodVisits.contains(clientMethodSourceLocationId), "Expected one deferred entry")

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))
      assert(recorder.deferredMethodVisits.isEmpty, "Expected no deferred entries")
      assert(recorder.deferredMapSourceLocations.isEmpty, "Expected no deferred entries")
      data.flush(); Thread.sleep(2500)
    }
  }

  describe("MethodVisit sent for shared method in two different source files") {
    it("should have duplicate node encounter with duplicate source locations") {
      val methodOneId = 30
      val methodOneSourceFile = 31
      val methodTwoId = 32
      val methodTwoSourceFile = 33
      val clientMethodId = 12
      val clientMethodSourceLocationId = 13

      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.sourceData.importSourceFiles(Map(methodOneSourceFile -> "C:\\package1\\source.java"))
      data.sourceData.importSourceFiles(Map(methodTwoSourceFile -> "C:\\package2\\source.java"))
      data.treeNodeData.storeNode(new TreeNodeData(methodOneId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), Option(methodOneSourceFile), None, None))
      data.treeNodeData.storeNode(new TreeNodeData(methodTwoId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), Option(methodTwoSourceFile), None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodOneId))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodTwoId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))

      recorder.processMessage(DataMessageContent.MapSourceLocation(clientMethodId, 1, 1, 1, 1, clientMethodSourceLocationId))
      assert(recorder.deferredMapSourceLocations.isEmpty, "Expected no deferred entries")

      recorder.processMessage(DataMessageContent.MethodVisit(clientMethodId, clientMethodSourceLocationId, 0, 0))
      assert(recorder.deferredMethodVisits.isEmpty, "Expected no deferred entries")

      data.flush(); Thread.sleep(2500)

      var methodOneSourceLocations = 0
      projectDb.withSession(implicit x => {
        methodOneSourceLocations = Q.queryNA[(Int)]("select COUNT(*) from \"source_location\" where \"source_file_id\"=" + methodOneSourceFile).first
      })
      assert(methodOneSourceLocations == 1)

      var methodTwoSourceLocations = 0
      projectDb.withSession(implicit x => {
        methodTwoSourceLocations = Q.queryNA[(Int)]("select COUNT(*) from \"source_location\" where \"source_file_id\"=" + methodTwoSourceFile).first
      })
      assert(methodTwoSourceLocations == 1)

      var methodOneNodeEncounters = 0
      projectDb.withSession(implicit x => {
        methodOneNodeEncounters = Q.queryNA[(Int)]("select COUNT(*) from \"node_encounters\" where \"node_id\"=" + methodOneId + " and \"source_location_id\"=1").first
      })
      assert(methodOneNodeEncounters == 1)

      var methodTwoNodeEncounters = 0
      projectDb.withSession(implicit x => {
        methodTwoNodeEncounters = Q.queryNA[(Int)]("select COUNT(*) from \"node_encounters\" where \"node_id\"=" + methodTwoId + " and \"source_location_id\"=2").first
      })
      assert(methodTwoNodeEncounters == 1)
    }
  }

  describe("MethodEntry sent for shared method in two different source files") {
    it("should have duplicate node encounter with no source locations") {
      val methodOneId = 32
      val methodTwoId = 34
      val clientMethodId = 13

      val recorder = new TraceRecorderDataProcessor(data, new TransientTraceData(data.id), None)
      data.treeNodeData.storeNode(new TreeNodeData(methodOneId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), None, None, None))
      data.treeNodeData.storeNode(new TreeNodeData(methodTwoId, None, "methodSig", CodeTreeNodeKind.Mth, Option[Int](50), None, None, None))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodOneId))
      data.treeNodeData.mapMethodSignature(MethodSignatureNode(0, "methodSig", methodTwoId))
      data.flush(); Thread.sleep(2500)

      recorder.processMessage(DataMessageContent.MapMethodSignature("methodSig", clientMethodId))
      recorder.processMessage(DataMessageContent.MethodEntry(clientMethodId, 0, 0))
      assert(recorder.deferredMethodEntries.isEmpty, "Expected no deferred entries")

      data.flush(); Thread.sleep(2500)

      var methodOneNodeEncounters = 0
      projectDb.withSession(implicit x => {
        methodOneNodeEncounters = Q.queryNA[(Int)]("select COUNT(*) from \"node_encounters\" where \"node_id\"=" + methodOneId).first
      })
      assert(methodOneNodeEncounters == 1)

      var methodTwoNodeEncounters = 0
      projectDb.withSession(implicit x => {
        methodTwoNodeEncounters = Q.queryNA[(Int)]("select COUNT(*) from \"node_encounters\" where \"node_id\"=" + methodTwoId).first
      })
      assert(methodTwoNodeEncounters == 1)
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

    projectsDb = makeDb(projectsDb, "projects-for-trace")
    projectDb = makeDb(projectDb, "project-for-trace")

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
}

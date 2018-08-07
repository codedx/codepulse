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

import java.util.zip.ZipFile

import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.data.model.slick.{ProjectMetadataDao, SlickProjectData, SlickProjectMetadataMaster}
import com.secdec.codepulse.data.storage.{InputStore, StorageManager}
import com.secdec.codepulse.tracer.ProjectManager
import com.secdec.codepulse.tracer.export.{ProjectImportReaderV1, ProjectImportReaderV2}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, FunSpec}

import scala.concurrent.duration._
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend
import scala.slick.jdbc.JdbcBackend.Database
import scala.slick.jdbc.{StaticQuery => Q}

class ImportSuite extends FunSpec with BeforeAndAfter with MockFactory {

  var data: SlickProjectData = _
  var projectsDb: JdbcBackend.DatabaseDef = _
  var projectDb: JdbcBackend.DatabaseDef = _

  describe("Version v2 of a project export file") {
    it("should import source file, location data, and include input file") {
      val file = new ZipFile(getClass.getResource("InvokeAMethod.v2.WithSourceFile.pulse").getPath)

      val mockInputStore = mock[InputStore]
      (mockInputStore.storeInput(_,_)).expects(*,*).once()

      val sourceFileSet = Set[(Int,String)](
        1 -> "com/codedx/invokeamethod/Main.java"
      )

      val sourceLocationSet = Set[(Int,Int,Int,Int,Option[Int],Option[Int])](
        (1, 1, 52, 52, None, None),
        (2, 1, 53, 53, None, None),
        (3, 1, 7, 7, None, None),
        (4, 1, 8, 8, None, None),
        (5, 1, 12, 12, None, None),
        (6, 1, 13, 13, None, None),
        (7, 1, 17, 17, None, None),
        (8, 1, 18, 18, None, None),
        (9, 1, 27, 27, None, None),
        (10, 1, 28, 28, None, None),
        (11, 1, 47, 47, None, None),
        (12, 1, 48, 48, None, None),
        (13, 1, 58, 58, None, None),
        (14, 1, 59, 59, None, None),
        (15, 1, 62, 62, None, None),
        (16, 1, 63, 63, None, None),
        (17, 1, 66, 66, None, None),
        (18, 1, 67, 67, None, None),
        (19, 1, 82, 82, None, None),
        (20, 1, 83, 83, None, None),
        (21, 1, 98, 98, None, None),
        (22, 1, 99, 99, None, None),
        (23, 1, 103, 103, None, None),
        (24, 1, 107, 107, None, None)
      )

      val treeNodeDataSet = Set[(Int,Option[Int], String, String, Option[Int], Option[Int], Option[Int], Option[Int])](
        (0,  None,		  "Classes",									              "g",	None,		    None, None, None),
        (3,  Option(0),	"com.codedx.invokeamethod",					      "p",	None,		    None, None, None),
        (4,  Option(3),	"Main",							                      "c",	None,		    Option(1), Option(4), None),
        (5,  Option(4),	"public void <init>()",						        "m",	Option(2),	Option(1), Option(5), Option(3)),
        (6,  Option(4),	"public static void Method1()",				    "m",	Option(2),	Option(1), Option(6), Option(7)),
        (7,  Option(4),	"public static void Method2()",				    "m",	Option(2),	Option(1), Option(7), Option(12)),
        (8,  Option(4),	"public static void Method3()",				    "m",	Option(2),	Option(1), Option(8), Option(17)),
        (9,  Option(4),	"public static void Method4()",				    "m",	Option(2),	Option(1), Option(9), Option(22)),
        (10, Option(4),	"public static void Method5()",				    "m",	Option(2),	Option(1), Option(10), Option(27)),
        (11, Option(4),	"public static void Method6()",				    "m",	Option(2),	Option(1), Option(11), Option(32)),
        (12, Option(4),	"public static void Method7()",				    "m",	Option(2),	Option(1), Option(12), Option(37)),
        (13, Option(4),	"public static void Method8()",				    "m",	Option(2),	Option(1), Option(13), Option(42)),
        (14, Option(4),	"public static void Method9()",				    "m",	Option(2),	Option(1), Option(14), Option(47)),
        (15, Option(4),	"public static void MethodInvoked(int)",	"m",	Option(9),	Option(1), Option(15), Option(52)),
        (16, Option(4),	"public static void main(String[])",		  "m",	Option(94),	Option(1), Option(16), Option(58)))

      val nodeEncountersSet = Set[(Option[Int],Int,Option[Int])](
        (Option(1),	10,	Option(10)),
        (Option(1),	10,  None),
        (Option(1),	10,	Option(9)),
        (Option(1),	15,	Option(2)),
        (Option(1),	15,  None),
        (Option(1),	15,	Option(1)),
        (Option(1),	6,  None),
        (Option(1),	6,	Option(3)),
        (Option(1),	6,	Option(4)),
        (Option(2),	14,  None),
        (Option(2),	14,	Option(11)),
        (Option(2),	14,	Option(12)),
        (Option(2),	15,	Option(2)),
        (Option(2),	15,	None),
        (Option(2),	15,	Option(1)),
        (None,	8,  None),
        (None,	16,	Option(16)),
        (None,	16,	Option(19)),
        (None,	16,	Option(24)),
        (None,	16,	Option(13)),
        (None,	16,	Option(20)),
        (None,	16,	Option(15)),
        (None,	16,	Option(21)),
        (None,	16,	Option(18)),
        (None,	8,	Option(8)),
        (None,	16,	Option(22)),
        (None,	16,	Option(14)),
        (None,	16,	Option(17)),
        (None,	7,	Option(6)),
        (None,	7,  None),
        (None,	16,  None),
        (None,	7,	Option(5)),
        (None,	16,	Option(23)),
        (None,	8,	Option(7))
      )

      assertV2Import(mockInputStore, file, sourceFileSet,sourceLocationSet, treeNodeDataSet, nodeEncountersSet)
    }
  }

  describe("Version v2 of a project created from a legacy export file") {
    it("should not include input file") {
      val file = new ZipFile(getClass.getResource("InvokeAMethod.v2.legacy.pulse").getPath)

      val mockInputStore2 = mock[InputStore]
      (mockInputStore2.storeInput(_,_)).expects(*,*).never()

      val sourceFileSet = Set.empty[(Int,String)]

      val sourceLocationsSet = Set.empty[(Int,Int,Int,Int,Option[Int],Option[Int])]

      val treeNodeDataSet = Set[(Int,Option[Int], String, String, Option[Int], Option[Int], Option[Int], Option[Int])](
        (0,  None,		  "Classes",									              "g",	None,		    None, None, None),
        (3,  Option(0),	"com.codedx.invokeamethod",					      "p",	None,		    None, None, None),
        (4,  Option(3),	"Main",							                      "c",	None,		    None, None, None),
        (5,  Option(4),	"public void <init>()",						        "m",	Option(2),	None, None, None),
        (6,  Option(4),	"public static void Method1()",				    "m",	Option(2),	None, None, None),
        (7,  Option(4),	"public static void Method2()",				    "m",	Option(2),	None, None, None),
        (8,  Option(4),	"public static void Method3()",				    "m",	Option(2),	None, None, None),
        (9,  Option(4),	"public static void Method4()",				    "m",	Option(2),	None, None, None),
        (10, Option(4),	"public static void Method5()",				    "m",	Option(2),	None, None, None),
        (11, Option(4),	"public static void Method6()",				    "m",	Option(2),	None, None, None),
        (12, Option(4),	"public static void Method7()",				    "m",	Option(2),	None, None, None),
        (13, Option(4),	"public static void Method8()",				    "m",	Option(2),	None, None, None),
        (14, Option(4),	"public static void Method9()",				    "m",	Option(2),	None, None, None),
        (15, Option(4),	"public static void MethodInvoked(int)",	"m",	Option(9),	None, None, None),
        (16, Option(4),	"public static void main(String[])",		  "m",	Option(94),	None, None, None))

      val nodeEncountersSet = Set[(Option[Int],Int,Option[Int])](
        (Option(1),	10,  None),
        (Option(1),	15,  None),
        (Option(1),	6,  None),
        (Option(2),	14,  None),
        (Option(2),	15,	None),
        (None,	8,  None),
        (None,	7,  None),
        (None,	16,  None)
      )

      assertV2Import(mockInputStore2, file, sourceFileSet, sourceLocationsSet, treeNodeDataSet, nodeEncountersSet)
    }
  }

  def assertV2Import(inputStore: InputStore, file: ZipFile,
                     sourceFileSet: Set[(Int,String)],
                     sourceLocationSet: Set[(Int,Int,Int,Int,Option[Int],Option[Int])],
                     treeNodeDataSet: Set[(Int,Option[Int], String, String, Option[Int], Option[Int], Option[Int], Option[Int])],
                     nodeEncountersSet: Set[(Option[Int],Int,Option[Int])]): Unit = {

    //beforeTest()

    val importer = new ProjectImportReaderV2()
    importer.doImport(inputStore, file, data)
    data.flush(); Thread.sleep(5000)

    assertMethodSignature(Set[(String,Int)](
      "com/codedx/invokeamethod/Main.<init>;1;()V" ->	5,
      "com/codedx/invokeamethod/Main.Method1;9;()V" -> 6,
      "com/codedx/invokeamethod/Main.Method2;9;()V" -> 7,
      "com/codedx/invokeamethod/Main.Method3;9;()V" -> 8,
      "com/codedx/invokeamethod/Main.Method4;9;()V" -> 9,
      "com/codedx/invokeamethod/Main.Method5;9;()V" -> 10,
      "com/codedx/invokeamethod/Main.Method6;9;()V" -> 11,
      "com/codedx/invokeamethod/Main.Method7;9;()V" -> 12,
      "com/codedx/invokeamethod/Main.Method8;9;()V" -> 13,
      "com/codedx/invokeamethod/Main.Method9;9;()V" -> 14,
      "com/codedx/invokeamethod/Main.MethodInvoked;9;(I)V" -> 15,
      "com/codedx/invokeamethod/Main.main;9;([Ljava/lang/String;)V" -> 16))

    assertSourceFile(sourceFileSet)

    assertSourceLocation(sourceLocationSet)

    assertRecordings(Set[Int](1,2))

    assertRecordingMetadata(Set[(Int,String,String)](
      (1,"running","false"),
      (2,"running","false"),
      (1,"clientLabel", "Recording 1"),
      (2,"clientLabel", "Recording 2"),
      (1,"clientColor", "#8cd98c"),
      (2,"clientColor", "#8cd9d9")
    ))

    assertTreeNodeData(treeNodeDataSet)

    assertTraceNodes(Set[(Int,Boolean)](
      0 -> true,
      3 -> true
    ))

    assertJspNodeMap(Set.empty[(String,Int)])

    assertTraceNodeFlags(Set.empty[(Int,String)])

    assertNodeEncounters(nodeEncountersSet)
  }

  describe("Version v1 of a project export file") {
    it("should import with no source file and source location data") {

      //beforeTest()

      val file = new ZipFile(getClass.getResource("InvokeAMethod.v1.pulse").getPath)
      val importer = new ProjectImportReaderV1()
      importer.doImport(StorageManager, file, data)
      data.flush(); Thread.sleep(5000)

      assertMethodSignature(Set[(String,Int)](
        "com/codedx/invokeamethod/Main.<init>;1;()V" ->	5,
        "com/codedx/invokeamethod/Main.Method1;9;()V" -> 6,
        "com/codedx/invokeamethod/Main.Method2;9;()V" -> 7,
        "com/codedx/invokeamethod/Main.Method3;9;()V" -> 8,
        "com/codedx/invokeamethod/Main.Method4;9;()V" -> 9,
        "com/codedx/invokeamethod/Main.Method5;9;()V" -> 10,
        "com/codedx/invokeamethod/Main.Method6;9;()V" -> 11,
        "com/codedx/invokeamethod/Main.Method7;9;()V" -> 12,
        "com/codedx/invokeamethod/Main.Method8;9;()V" -> 13,
        "com/codedx/invokeamethod/Main.Method9;9;()V" -> 14,
        "com/codedx/invokeamethod/Main.MethodInvoked;9;(I)V" -> 15,
        "com/codedx/invokeamethod/Main.main;9;([Ljava/lang/String;)V" -> 16))

      assertSourceFile(Set.empty[(Int,String)])

      assertSourceLocation(Set.empty[(Int,Int,Int,Int,Option[Int],Option[Int])])

      assertRecordings(Set[Int](1,2))

      assertRecordingMetadata(Set[(Int,String,String)](
        (1,"running","false"),
        (2,"running","false"),
        (1,"clientLabel", "Recording 1"),
        (2,"clientLabel", "Recording 9"),
        (1,"clientColor", "#8cd98c"),
        (2,"clientColor", "#8cd9d9")
      ))

      assertTreeNodeData(Set[(Int,Option[Int], String, String, Option[Int], Option[Int], Option[Int], Option[Int])](
        (0,		None,		    "Classes",								                "g",	None,		    None, None, None),
        (3,		Option(0),	"com.codedx.invokeamethod",				        "p",	None,		    None, None, None),
        (4,		Option(3),	"Main",									                  "c",	None,		    None, None, None),
        (5,		Option(4),	"public void <init>()",				            "m",	Option(2),	None, None, None),
        (6,		Option(4),	"public static void Method1()",			      "m",	Option(2),	None, None, None),
        (7,		Option(4),	"public static void Method2()",			      "m",	Option(2),	None, None, None),
        (8,		Option(4),	"public static void Method3()",			      "m",	Option(2),	None, None, None),
        (9,		Option(4),	"public static void Method4()",			      "m",	Option(2),	None, None, None),
        (10,	Option(4),	"public static void Method5()",			      "m",	Option(2),	None, None, None),
        (11,	Option(4),	"public static void Method6()",			      "m",	Option(2),	None, None, None),
        (12,	Option(4),	"public static void Method7()",			      "m",	Option(2),	None, None, None),
        (13,	Option(4),	"public static void Method8()",			      "m",	Option(2),	None, None, None),
        (14,	Option(4),	"public static void Method9()",			      "m",	Option(2),	None, None, None),
        (15,	Option(4),	"public static void MethodInvoked(int)",	"m",	Option(9),	None, None, None),
        (16,	Option(4),	"public static void main(String[])",		  "m",	Option(94),	None, None, None)
      ))

      assertTraceNodes(Set[(Int,Boolean)](
        0 -> true,
        3 -> true
      ))

      assertJspNodeMap(Set.empty[(String,Int)])

      assertTraceNodeFlags(Set.empty[(Int,String)])

      assertNodeEncounters(Set[(Option[Int],Int,Option[Int])](
        (Option(1), 15, None),
        (Option(1), 6,  None),
        (Option(1), 10, None),
        (Option(2), 15, None),
        (Option(2), 14, None),
        (None, 7, None),
        (None, 16, None),
        (None, 8, None)
      ))
    }
  }

  def assertMethodSignature(expected: Set[(String,Int)]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(String,Int)]
    projectDb.withSession(implicit x => {
      Q.queryNA[(String,Int)]("select \"sig\",\"node_id\" from \"method_signature_node_map\"").foreach( s => {
        actual.append(s._1 -> s._2)
      })
    })
    assert(actual.toSet == expected)
  }

  def assertSourceFile(expected: Set[(Int,String)]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Int,String)]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Int,String)]("select * from \"source_file\"").foreach( s => {
        actual.append(s._1 -> s._2)
      })
    })
    assert(actual.toSet == expected)
  }

  def assertSourceLocation(expected: Set[(Int,Int,Int,Int,Option[Int],Option[Int])]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Int,Int,Int,Int,Option[Int],Option[Int])]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Int,Int,Int,Int,Option[Int],Option[Int])]("select * from \"source_location\"").foreach( s => {
        actual.append((s._1, s._2, s._3, s._4, s._5, s._6))
      })
    })
    assert(actual.toSet == expected)
  }

  def assertRecordings(expected: Set[(Int)]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Int)]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Int)]("select * from \"recordings\"").foreach( s => {
        actual.append(s)
      })
    })
    assert(actual.toSet == expected)
  }

  def assertRecordingMetadata(expected: Set[(Int,String,String)]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Int,String,String)]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Int,String,String)]("select * from \"recording_metadata\"").foreach( s => {
        actual.append((s._1, s._2, s._3))
      })
    })
    assert(actual.toSet == expected)
  }

  def assertTreeNodeData(expected: Set[(Int,Option[Int], String, String, Option[Int], Option[Int], Option[Int], Option[Int])]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Int,Option[Int], String, String, Option[Int], Option[Int], Option[Int], Option[Int])]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Int,Option[Int], String, String, Option[Int], Option[Int], Option[Int], Option[Int])]("select * from \"tree_node_data\"").foreach( s => {
        actual.append((s._1, s._2, s._3, s._4, s._5, s._6, s._7, s._8))
      })
    })
    assert(actual.toSet == expected)
  }

  def assertTraceNodes(expected: Set[(Int,Boolean)]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Int,Boolean)]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Int,Boolean)]("select * from \"traced_nodes\"").foreach( s => {
        actual.append((s._1, s._2))
      })
    })
    assert(actual.toSet == expected)
  }

  def assertJspNodeMap(expected: Set[(String,Int)]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(String,Int)]
    projectDb.withSession(implicit x => {
      Q.queryNA[(String,Int)]("select * from \"jsp_node_map\"").foreach( s => {
        actual.append((s._1, s._2))
      })
    })
    assert(actual.toSet == expected)
  }

  def assertTraceNodeFlags(expected: Set[(Int,String)]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Int,String)]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Int,String)]("select * from \"tree_node_flags\"").foreach( s => {
        actual.append((s._1, s._2))
      })
    })
    assert(actual.toSet == expected)
  }

  def assertNodeEncounters(expected: Set[(Option[Int],Int,Option[Int])]): Unit = {
    val actual = collection.mutable.ListBuffer.empty[(Option[Int],Int,Option[Int])]
    projectDb.withSession(implicit x => {
      Q.queryNA[(Option[Int],Int,Option[Int])]("select * from \"node_encounters\"").foreach( s => {
        actual.append((s._1, s._2, s._3))
      })
    })
    assert(actual.toSet == expected)
  }

  before {
    def makeDb(db: JdbcBackend.DatabaseDef, dbName: String): JdbcBackend.DatabaseDef = {
      if (db == null) {
        Database.forURL(s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
      } else {
        db.withSession(implicit x => Q.updateNA("drop all objects").execute())
        db
      }
    }

    projectsDb = makeDb(projectsDb, "projects-for-import")
    projectDb = makeDb(projectDb, "project-for-import")

    val projectMetadataDao = new ProjectMetadataDao(H2Driver)

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

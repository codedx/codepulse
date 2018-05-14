package com.secdec.codepulse.tracer.export

import java.io.InputStream
import java.util.zip.ZipFile

import com.fasterxml.jackson.core.JsonToken
import com.secdec.codepulse.data.model._
import net.liftweb.util.Helpers.AsInt

class ProjectImportReaderV2 extends ProjectImportReaderV1 with ProjectImportHelpers with JsonHelpers {
  override def doImport(zip: ZipFile, destination: ProjectData): Unit = {

    readSourceFilesJson(zip, destination)
    val sourceLocationMap = readSourceLocationsJson(zip, destination)

    readProjectJson(zip, destination)
    readNodesJson(zip, destination)
    readMethodMappingsJson(zip, destination)
    readJspMappingsJson(zip, destination)
    val recMap = readRecordingsJson(zip, destination)
    readEncountersJsonWithSourceLocation(zip, recMap, sourceLocationMap, destination)
  }

  protected def readSourceFilesJson(zip: ZipFile, destination: ProjectData): Unit = {
    read(zip, "sourceFiles.json") { readSourceFiles(_, destination.sourceData) }
  }

  protected def readSourceLocationsJson(zip: ZipFile, destination: ProjectData): Map[Int,Int] = {
    read(zip, "sourceLocations.json", Map.empty[Int, Int]) { readSourceLocations(_, destination.sourceData) }
  }

  protected def readEncountersJsonWithSourceLocation(zip: ZipFile, recMap: Map[Int,Int], sourceLocations: Map[Int, Int], destination: ProjectData): Unit = {
    read(zip, "encounters.json") { readEncounters(_, recMap, sourceLocations, destination.encounters) }
  }

  private def readEncounters(in: InputStream, recordingMap: Map[Int, Int], sourceLocations: Map[Int, Int], encounters: TraceEncounterDataAccess) {
    import JsonToken._

    readJson(in) { jp =>
      if (jp.nextToken != START_OBJECT)
        throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

      var all = Nil: List[String]
      var inRec = collection.mutable.Set.empty[String]

      while (jp.nextValue != END_OBJECT) {
        val rec = jp.getCurrentName

        if (jp.getCurrentToken != START_ARRAY)
          throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_ARRAY.")

        val values = collection.mutable.ListBuffer.empty[(Int, Option[Int])]

        while (jp.nextValue != END_ARRAY) {

          if (jp.getCurrentToken != START_OBJECT)
            throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

          var nodeId = None: Option[Int]
          var sourceLocationId = None: Option[Int]

          while (jp.nextValue != END_OBJECT) {
            jp.getCurrentName match {
              case "nodeId" => nodeId = Some(jp.getIntValue)
              case "sourceLocationId" => sourceLocationId = Some(jp.getIntValue)
            }
          }
          values.append(nodeId.get -> sourceLocationId)
        }

        rec match {
          case "all" => all = values.map(x => x._1.toString + ":" + (if (x._2.isEmpty) "?" else x._2.get.toString)).toList

          case AsInt(recId) =>
            val result = values.map(x => x._1.toString + ":" + (if (x._2.isEmpty) "?" else x._2.get.toString)).toList
            inRec ++= result

            encounters.record(recordingMap(recId) :: Nil, values.result())

          case _ => throw new ProjectImportException("Invalid recording ID for encounters map.")
        }
      }

      // we only need to store (all - inRec)
      val allMinusRecordings = all.toSet -- inRec

      val result = allMinusRecordings.map(x => {
        val nodeId = x.substring(0, x.indexOf(":")).toInt
        val sourceLocationIdString = x.substring(x.indexOf(":") + 1)
        val sourceLocationId = if (sourceLocationIdString == "?") None else Option(sourceLocationIdString.toInt)
        nodeId -> sourceLocationId
      })
      encounters.record(Nil, result.toList)

      if (jp.nextToken != null)
        throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
    }
  }

  private def readSourceFiles(in: InputStream, sourceDataAccess: SourceDataAccess) {
    import JsonToken._

    readJson(in) { jp =>
      if (jp.nextToken != START_ARRAY)
        throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_ARRAY.")

      val sourceFiles = collection.mutable.Map.empty[(String, String), Int]
      def flushBuffer() { sourceDataAccess.importSourceFiles(sourceFiles.toMap); sourceFiles.clear }
      def checkAndFlush() { if (sourceFiles.size >= 500) flushBuffer() }

      while (jp.nextValue != END_ARRAY) {
        if (jp.getCurrentToken != START_OBJECT)
          throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

        var id = None: Option[Int]
        var group = None: Option[String]
        var path = None: Option[String]

        while (jp.nextValue != END_OBJECT) {
          jp.getCurrentName match {
            case "id" => id = Some(jp.getIntValue)
            case "group" => group = Some(jp.getText)
            case "path" => path = Some(jp.getText)
          }
        }

        sourceFiles.put((group.get, path.get), id.get)
        checkAndFlush()
      }

      flushBuffer()

      if (jp.nextToken != null)
        throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
    }
  }

  private def readSourceLocations(in: InputStream, sourceDataAccess: SourceDataAccess): Map[Int, Int] = {
    import JsonToken._

    val idMap = collection.mutable.Map.empty[Int, Int]

    readJson(in) { jp =>
      if (jp.nextToken != START_ARRAY)
        throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_ARRAY.")

      while (jp.nextValue != END_ARRAY) {
        if (jp.getCurrentToken != START_OBJECT)
          throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected START_OBJECT.")

        var id = None: Option[Int]
        var sourceFileId = None: Option[Int]
        var startLine = None: Option[Int]
        var endLine = None: Option[Int]
        var startCharacter = None: Option[Int]
        var endCharacter = None: Option[Int]

        while (jp.nextValue != END_OBJECT) {
          jp.getCurrentName match {
            case "id" => id = Some(jp.getIntValue)
            case "sourceFileId" => sourceFileId = Some(jp.getIntValue)
            case "startLine" => startLine = Some(jp.getIntValue)
            case "endLine" => endLine = Some(jp.getIntValue)
            case "startCharacter" => startCharacter = Some(jp.getIntValue)
            case "endCharacter" => endCharacter = Some(jp.getIntValue)
          }
        }

        val sourceLocationId = sourceDataAccess.getSourceLocationId(sourceFileId.get, startLine.get, endLine.get, startCharacter, endCharacter)
        idMap.put(id.get, sourceLocationId)
      }

      if (jp.nextToken != null)
        throw new ProjectImportException(s"Unexpected token ${jp.getCurrentToken}; expected EOF.")
    }

    idMap.toMap
  }
}
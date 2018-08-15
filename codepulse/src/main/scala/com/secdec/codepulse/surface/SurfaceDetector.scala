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

package com.secdec.codepulse.surface

import java.util.List
import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.data.storage.StorageManager
import com.denimgroup.threadfix.data.interfaces.Endpoint
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabase
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabaseFactory
import com.denimgroup.threadfix.framework.util.EndpointUtil
import collection.JavaConverters._

import com.secdec.codepulse.processing.ProcessStatusFinishedPayload

sealed trait SurfaceDetectorStatus
sealed trait TransientSurfaceDetectorStatus extends SurfaceDetectorStatus
object SurfaceDetectorStatus {
  case object Running extends TransientSurfaceDetectorStatus
  case class  Finished(surfaceMethodCount: Int) extends TransientSurfaceDetectorStatus
  case object Failed extends TransientSurfaceDetectorStatus
  case object Unknown extends TransientSurfaceDetectorStatus
}

case class SurfaceDetectorFinishedPayload(surfaceMethodCount: Int) extends ProcessStatusFinishedPayload

object SurfaceDetector {
  def run(id: ProjectId): Int = {

    val path = StorageManager.getExtractedStorageFor(id)

    val database: EndpointDatabase = EndpointDatabaseFactory.getDatabase(path)
    var endpoints: List[Endpoint] = database.generateEndpoints
    endpoints = EndpointUtil.flattenWithVariants(endpoints)

    endpoints.asScala.map(endpoint => {
      val filePath = endpoint.getFilePath
      val start = endpoint.getStartingLineNumber
      val end = endpoint.getEndingLineNumber
      println(s"Path: $filePath ($start -> $end)")
    })

    endpoints.size
  }
}
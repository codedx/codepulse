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

import java.io.File

import com.denimgroup.threadfix.framework.engine.framework.FrameworkCalculator
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabaseFactory
import com.denimgroup.threadfix.framework.util.EndpointUtil
import com.secdec.codepulse.processing.ProcessStatusFinishedPayload

import scala.collection.JavaConverters._

sealed trait SurfaceDetectorStatus
sealed trait TransientSurfaceDetectorStatus extends SurfaceDetectorStatus
object SurfaceDetectorStatus {
  case object Running extends TransientSurfaceDetectorStatus
  case class  Finished(surfaceMethodCount: Int) extends TransientSurfaceDetectorStatus
  case object Failed extends TransientSurfaceDetectorStatus
  case object Unknown extends TransientSurfaceDetectorStatus
}

case class SurfaceDetectorFinishedPayload(surfaceMethodCount: Int) extends ProcessStatusFinishedPayload

case class SurfaceEndpoint(filePath: String, startingLineNumber: Int, endingLineNumber: Int)

object SurfaceDetector {
  def run(path: File): Seq[SurfaceEndpoint] = {

    val frameworkTypes = FrameworkCalculator.getTypes(path)
    val databases = frameworkTypes.asScala.map(f => EndpointDatabaseFactory.getDatabase(path, f))

    if (!databases.isEmpty) {
      databases.flatMap(_.generateEndpoints().asScala)
        .map(x => SurfaceEndpoint(x.getFilePath, x.getStartingLineNumber, x.getEndingLineNumber))
        .distinct
    } else {
      Seq.empty[SurfaceEndpoint]
    }
  }
}
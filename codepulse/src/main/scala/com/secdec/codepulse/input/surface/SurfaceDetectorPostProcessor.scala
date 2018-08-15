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

package com.secdec.codepulse.input.surface

import akka.actor.{Actor, Stash}
import com.codedx.codepulse.utility.Loggable
import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.processing.ProcessStatus.ProcessDataAvailable
import com.secdec.codepulse.processing.{ProcessEnvelope, ProcessStatus}
import com.secdec.codepulse.surface.{SurfaceDetector, SurfaceDetectorFinishedPayload}
import com.secdec.codepulse.util.Throwable

class SurfaceDetectorPostProcessor(eventBus: GeneralEventBus) extends Actor with Stash with Loggable {

  private val surfaceDetectorActionName = "Surface Detector"

  def receive = {
    case ProcessEnvelope(_, ProcessDataAvailable(identifier, storage, _, _)) => {
      try {
        def status(processStatus: ProcessStatus): Unit = {
          eventBus.publish(processStatus)
        }

        status(ProcessStatus.Running(identifier, surfaceDetectorActionName))

        val endpointCount = SurfaceDetector.run(ProjectId(identifier.toInt))
        status(ProcessStatus.Finished(identifier, surfaceDetectorActionName, Some(SurfaceDetectorFinishedPayload(endpointCount))))
      } catch {
        case exception: Exception => {
          logger.error(s"Surface detector failed with error: ${Throwable.getStackTraceAsString(exception)}")
          eventBus.publish(ProcessStatus.Failed(identifier, surfaceDetectorActionName, Some(exception)))
        }
      }
    }
  }
}

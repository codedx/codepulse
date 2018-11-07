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

package com.secdec.codepulse.components.surface

import akka.actor.Actor
import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.processing.{ProcessEnvelope, ProcessStatus}
import com.secdec.codepulse.surface.{SurfaceDetectorFinishedPayload, SurfaceDetectorStatus}
import com.secdec.codepulse.tracer.projectDataProvider
import com.secdec.codepulse.util.comet.PublicCometInit
import net.liftweb.http.CometActor
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds.jsExpToJsCmd
import net.liftweb.http.js._
import net.liftweb.http.js.jquery.JqJE._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._

class Updates extends Actor {

  private val surfaceDetectorActionName = "Surface Detector"

  def receive = {

    case ProcessEnvelope(_, ProcessStatus.Running(identifier, action)) if action == surfaceDetectorActionName => {
      val project = projectDataProvider getProject ProjectId(identifier.toInt)
      project.metadata.surfaceDetectorStatus = SurfaceDetectorStatus.Running
      Updates.pushUpdate(identifier, ("state" -> "running"))
    }

    case ProcessEnvelope(_, ProcessStatus.Finished(identifier, action, payload @ Some(_))) if action == surfaceDetectorActionName => {
      if (payload.isDefined && payload.get.isInstanceOf[SurfaceDetectorFinishedPayload]) {
        val surfaceDetectorPayload = payload.get.asInstanceOf[SurfaceDetectorFinishedPayload]
        val project = projectDataProvider getProject ProjectId(identifier.toInt)
        project.metadata.surfaceDetectorStatus =  SurfaceDetectorStatus.Finished(surfaceDetectorPayload.surfaceMethodCount)
        Updates.pushUpdate(identifier, (("state" -> "finished") ~ ("surfaceMethodCount" -> surfaceDetectorPayload.surfaceMethodCount)))
      }
    }

    case ProcessEnvelope(_, ProcessStatus.Failed(identifier, action, _)) if action == surfaceDetectorActionName => {
      val project = projectDataProvider getProject ProjectId(identifier.toInt)
      project.metadata.surfaceDetectorStatus = SurfaceDetectorStatus.Failed
      Updates.pushUpdate(identifier, ("state" -> "failed"))
    }

    case _ =>
  }
}

object Updates extends CometActor with PublicCometInit {
  def pushUpdate(projectId: String, status: JObject) {
    val update = ("project" -> projectId) ~
      ("surfacedetector_update" -> status)
    val cmd: JsCmd = Jq(JsVar("document")) ~> JsFunc("trigger", "surfacedetector-update", update)
    partialUpdate {cmd}
  }

  def render = Nil
}

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

package com.secdec.codepulse.components.dependencycheck

import akka.actor.Actor
import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.dependencycheck.DependencyCheckStatus
import com.secdec.codepulse.processing.{ ProcessEnvelope, ProcessStatus }
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
	def receive = {
		case ProcessEnvelope(_, ProcessStatus.Queued(identifier)) => {
			val project = projectDataProvider getProject ProjectId(identifier.toInt)
			project.metadata.dependencyCheckStatus = DependencyCheckStatus.Queued
			Updates.pushUpdate(identifier, ("state" -> "queued"))
		}

		case ProcessEnvelope(_, ProcessStatus.Running(identifier)) => {
			val project = projectDataProvider getProject ProjectId(identifier.toInt)
			project.metadata.dependencyCheckStatus = DependencyCheckStatus.Running
			Updates.pushUpdate(identifier, ("state" -> "running"))
		}

		case ProcessEnvelope(_, ProcessStatus.Finished(identifier, Some((dependencies: Int, vulnerableDependencies: Int, vulnerableNodes: Seq[Int])))) => {
			val project = projectDataProvider getProject ProjectId(identifier.toInt)
			project.metadata.dependencyCheckStatus = DependencyCheckStatus.Finished(dependencies, vulnerableDependencies)
			Updates.pushUpdate(identifier, ("state" -> "finished") ~ ("numDeps" -> dependencies) ~ ("numFlaggedDeps" -> vulnerableDependencies),
				vulnerableNodes)
		}

		case ProcessEnvelope(_, ProcessStatus.Failed(identifier, action, _)) if action == "Dependency Check" => {
			val project = projectDataProvider getProject ProjectId(identifier.toInt)
			project.metadata.dependencyCheckStatus = DependencyCheckStatus.Failed
			Updates.pushUpdate(identifier, ("state" -> "failed"))
		}

		case ProcessEnvelope(_, ProcessStatus.NotRun(identifier)) => {
			val project = projectDataProvider getProject ProjectId(identifier.toInt)
			project.metadata.dependencyCheckStatus = DependencyCheckStatus.NotRun
			Updates.pushUpdate(identifier, ("state" -> "none"))
		}

		case ProcessEnvelope(_, ProcessStatus.Unknown(identifier)) => {
			val project = projectDataProvider getProject ProjectId(identifier.toInt)
			project.metadata.dependencyCheckStatus = DependencyCheckStatus.Unknown
			Updates.pushUpdate(identifier, ("state" -> "unknown"))
		}

		case _ =>
	}
}

object Updates extends CometActor with PublicCometInit {
	def pushUpdate(projectId: String, status: JObject, vulnerableNodes: Seq[Int]) {
		val update = ("project" -> projectId) ~
			("summary" -> status) ~ ("vulnerableNodes" -> vulnerableNodes)
		val cmd: JsCmd = Jq(JsVar("document")) ~> JsFunc("trigger", "dependencycheck-update", update)
		partialUpdate {cmd}
	}

	def pushUpdate(projectId: String, status: JObject) {
		val update = ("project" -> projectId) ~
			("summary" -> status) ~ ("vulnerableNodes" -> List.empty[JObject])
		val cmd: JsCmd = Jq(JsVar("document")) ~> JsFunc("trigger", "dependencycheck-update", update)
		partialUpdate {cmd}
	}

	def render = Nil
}
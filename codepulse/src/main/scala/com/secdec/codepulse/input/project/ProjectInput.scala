/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2017 Applied Visions - http://securedecisions.avi.com
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

package com.secdec.codepulse.input.project

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{ Actor, Stash }
import com.secdec.codepulse.data.model.{ ProjectData, ProjectId }
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.processing.ProcessEnvelope
import com.secdec.codepulse.processing.ProcessStatus._
import com.secdec.codepulse.tracer.{ generalEventBus, projectDataProvider, projectManager }

trait ProjectLoader {
	def createAndLoadProjectData(doLoad: (ProjectData, GeneralEventBus) => Unit): ProjectData
}

case class CreateProject(load: (ProjectData, GeneralEventBus) => Unit)

class ProjectInputActor extends Actor with Stash with ProjectLoader {

	// TODO: handle data input by creating a project and broadcasting with 'DataInputAvailable' with project payload
	// TODO: capture failed state to cause a failed message and redirect (as necessary) for the user

	def receive = {
		case CreateProject(load) => {
			val projectData = createAndLoadProjectData(load)
			sender ! projectData
		}
		case ProcessEnvelope(_, ProcessDataAvailable(identifier, file, treeNodeData)) => {
			for (target <- projectManager getProject ProjectId(identifier.toInt)) {
				target.notifyLoadingFinished()
			}
		}
		case ProcessEnvelope(_, Failed(identifier, action, Some(exception))) if action != "Dependency Check" => {
			for (target <- projectManager.removeUnloadedProject(ProjectId(identifier.toInt))) {
				target.notifyLoadingFailed()
			}
		}
	}

	def createAndLoadProjectData(doLoad: (ProjectData, GeneralEventBus) => Unit): ProjectData = {
		val projectId = projectManager.createProject
		val projectData = projectDataProvider getProject projectId

		doLoad(projectData, generalEventBus)

		projectData
	}
}

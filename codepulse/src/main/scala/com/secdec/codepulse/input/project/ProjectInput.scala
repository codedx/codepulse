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

import akka.actor.{ Actor, ActorRef, Stash }
import scala.collection.mutable.{ Map => MutableMap }
import com.secdec.codepulse.data.model.{ ProjectData, ProjectId }
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.LanguageProcessor
import com.secdec.codepulse.processing.ProcessEnvelope
import com.secdec.codepulse.processing.ProcessStatus._
import com.secdec.codepulse.tracer.{ BootVar, generalEventBus, projectDataProvider, projectManager }

trait ProjectLoader {
	protected def createProject: ProjectData
}

case class CreateProject(processors: List[BootVar[ActorRef]], load: (ProjectData, GeneralEventBus) => Unit)

class ProjectInputActor extends Actor with Stash with ProjectLoader {

	val projectCreationProcessors = MutableMap.empty[String, List[BootVar[ActorRef]]]

	val projectProcessingSuccesses = MutableMap.empty[String, Integer]

	val projectProcessingFailures = MutableMap.empty[String, Integer]

	// TODO: handle data input by creating a project and broadcasting with 'DataInputAvailable' with project payload
	// TODO: capture failed state to cause a failed message and redirect (as necessary) for the user

	def receive = {
		case CreateProject(processors, load) => {
			val projectData = createProject
			addProjectCreators(projectData, processors)

			load(projectData, generalEventBus)

			sender ! projectData
		}
		case ProcessEnvelope(_, ProcessDataAvailable(identifier, file, treeNodeData, sourceData)) => {
			val numberOfProcessors = projectCreationProcessors.get(identifier).get.length
			val succeeded = projectProcessorSucceeded(identifier)
			val failed = projectProcessingFailures.get(identifier).get

			val remaining = numberOfProcessors - (succeeded + failed)

			if(remaining == 0 && succeeded >= 1) {
				projectManager getProject ProjectId(identifier.toInt) foreach(_.notifyLoadingFinished())
				clearProjectCreators(identifier)
			}
		}
		case ProcessEnvelope(_, Failed(identifier, action, Some(exception))) if action != "Dependency Check" => {
			val numberOfProcessors = projectCreationProcessors.get(identifier).get.length
			val succeeded = projectProcessingSuccesses.get(identifier).get
			val failed = projectProcessorFailed(identifier)

			val remaining = numberOfProcessors - (succeeded + failed)

			if(remaining == 0 && failed == numberOfProcessors) {
				projectManager.removeUnloadedProject(ProjectId(identifier.toInt), exception.getMessage)
				clearProjectCreators(identifier)
			}
		}
	}

	protected def createProject: ProjectData = {
		val projectId = projectManager.createProject
		val projectData = projectDataProvider getProject projectId

		projectData
	}

	protected def addProjectCreators(projectData: ProjectData, processors: List[BootVar[ActorRef]]): Integer = {
		val id = projectData.id.num.toString
		projectCreationProcessors.put(id, processors)
		projectProcessingSuccesses.put(id, 0)
		projectProcessingFailures.put(id, 0)
		processors.length
	}

	protected def clearProjectCreators(projectId: String) = {
		projectCreationProcessors.remove(projectId)
		projectProcessingSuccesses.remove(projectId)
		projectProcessingFailures.remove(projectId)
	}

	protected def projectProcessorSucceeded(projectId: String): Integer = {
		incrementProcessor(projectId, projectProcessingSuccesses)
	}

	protected def projectProcessorFailed(projectId: String) = {
		incrementProcessor(projectId, projectProcessingFailures)
	}

	private def incrementProcessor(key: String, map: MutableMap[String, Integer]): Integer = {
		val count = map.get(key).get + 1
		map.update(key, count)

		count
	}
}

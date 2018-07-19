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

import java.io.File

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorRef, Stash}
import com.codedx.codepulse.utility.Loggable

import scala.collection.mutable.{Map => MutableMap}
import com.secdec.codepulse.data.model.{ProjectData, ProjectId}
import com.secdec.codepulse.data.storage.{Storage, StorageManager}
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.processing.ProcessEnvelope
import com.secdec.codepulse.processing.ProcessStatus._
import com.secdec.codepulse.tracer.{BootVar, generalEventBus, projectDataProvider, projectManager}

trait ProjectLoader {
	protected def createProject: ProjectData
}

case class CreateProject(inputFile: File, load: (ProjectData, Storage, GeneralEventBus) => Unit)

class ProjectInputActor extends Actor with Stash with ProjectLoader with Loggable {

	import com.secdec.codepulse.util.Actor._

	val projectCreationProcessors = MutableMap.empty[String, List[BootVar[ActorRef]]]

	val projectProcessingSuccesses = MutableMap.empty[String, Integer]

	val projectProcessingFailures = MutableMap.empty[String, Integer]

	def receive = {
		case CreateProject(inputFile, load) => {
			try {
				val projectData = createProject
				StorageManager.storeInput(projectData.id, inputFile) match {
					case Some(storage) =>
						load(projectData, storage, generalEventBus)
						sender ! projectData
					case _ => sender ! akka.actor.Status.Failure(new IllegalStateException(s"Unable to create storage for project ${projectData.id.num}"))
				}
			}
			catch {
				case ex: Exception => {
					logAndSendFailure(logger, "Unable to complete create-project operation for input file", sender, ex)
				}
			}
		}
		case ProcessEnvelope(_, ProcessDataAvailable(identifier, _, _, _)) => {
				projectManager getProject ProjectId(identifier.toInt) foreach(_.notifyLoadingFinished())
		}
		case ProcessEnvelope(_, Failed(identifier, action, Some(exception))) if action != "Dependency Check" => {
				projectManager.removeUnloadedProject(ProjectId(identifier.toInt), exception.getMessage)
		}
	}

	protected def createProject: ProjectData = {
		val projectId = projectManager.createProject
		val projectData = projectDataProvider getProject projectId

		projectData
	}
}

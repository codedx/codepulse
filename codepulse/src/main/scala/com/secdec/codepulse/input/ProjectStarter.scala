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

package com.secdec.codepulse.input

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.secdec.codepulse.data.bytecode.{ CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.model.{ ProjectData, ProjectId, TreeNodeImporter }
import com.secdec.codepulse.tracer.{ projectDataProvider, projectManager }

trait ProjectStarter {
	this: BuilderFromArchive[CodeForestBuilder] =>


	def createAndLoadProjectData(doLoad: ProjectData => Unit) = {
		val projectId = projectManager.createProject
		val projectData = projectDataProvider getProject projectId

		val futureLoad = Future {
			doLoad(projectData)
		}

		futureLoad onComplete {
			case util.Failure(exception) =>
				println(s"Error importing file: $exception")
				exception.printStackTrace()
				projectManager.removeUnloadedProject(projectId)

			case util.Success(_) =>
				for (target <- projectManager getProject projectId) {
					target.notifyLoadingFinished()
				}
		}

		projectData
	}

	def process(): ProjectData = {
		println("~~~~~~~~~processing project")
		createAndLoadProjectData {
			projectData => {
				process(projectData)
				projectData
			}
		}
	}
}

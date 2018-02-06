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

package com.secdec.codepulse.tracer

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.secdec.codepulse.components.dependencycheck.{ Updates => DependencyCheckUpdates }
import com.secdec.codepulse.data.model.{ ProjectData, ProjectId }
import com.secdec.codepulse.tracer.export.ProjectImporter

object ProjectUploadData {

	def handleProjectExport(file: File, cleanup: => Unit): ProjectId = createAndLoadProjectData { projectData =>

		// Note: the `creationDate` should have been filled in by the importer.
		//The `importDate` is now.
		projectData.metadata.importDate = Some(System.currentTimeMillis)

		try {
			ProjectImporter.importFrom(file, projectData)
		} finally {
			cleanup
		}
	}

	/** A preliminary check on a File to see if it looks like an
	  * exported .pulse file.
	  */
	def checkForProjectExport(file: File): Boolean = {
		ProjectImporter.canImportFrom(file)
	}

	/** Immediately adds a new Project to the projectManager, and returns its id.
	  * Meanwhile, it starts a task that will call `doLoad` on the id and the
	  * newly-initialized ProjectData instance. When the `doLoad` task completes,
	  * if it was a failure, the project will be removed from the manager. If
	  * the loading process finished successfully, the associated TracingTarget
	  * will be notified so that it can leave the 'loading' state.
	  *
	  * The goal of this is to allow users to immediately navigate to the new
	  * project page when they upload a file, without having to wait for the actual
	  * (heavy-duty) file processing logic. Simple checks should be performed on
	  * any file before processing it in this way, to avoid a useless redirect.
	  *
	  * An example:
	  * If a user uploads a huge .war file, processing may take around a minute.
	  * But since the processing is done elsewhere, the user can see that the
	  * upload itself was successful; they will see a 'loading' screen on the
	  * project page, rather than waiting for a progress bar in the upload form.
	  */
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

		projectId
	}
}


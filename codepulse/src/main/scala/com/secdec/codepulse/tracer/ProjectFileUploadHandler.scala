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
import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import com.secdec.codepulse.data.model.{ProjectData, ProjectId}
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.CanProcessFile
import com.secdec.codepulse.input.project.CreateProject
import com.secdec.codepulse.pages.traces.ProjectDetailsPage
import com.secdec.codepulse.processing.ProcessStatus
import com.secdec.codepulse.util.ManualOnDiskFileParamHolder
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JsonDSL._

case class ProjectFileUploadError(message: String) extends LiftResponse with HeaderDefaults {
	def toResponse = InMemoryResponse(message.getBytes, headers, cookies, 500)
}

class ProjectFileUploadHandler(projectManager: ProjectManager, eventBus: GeneralEventBus) extends RestHelper with Loggable {

	def initializeServer() = {
		LiftRules.statelessDispatch.append(this)
		this
	}

	def logAndMakeProjectFileUploadError(message: String, throwable: Throwable): Box[ProjectFileUploadError] = {
		import com.secdec.codepulse.util.Throwable._
		logger.error(s"$message - ${getStackTraceAsString(throwable)}")

		val throwableMessage = throwable.getMessage
		val errorMessage = if (throwableMessage == null) "no error details available" else throwableMessage
		Full(ProjectFileUploadError(s"$message - $errorMessage"))
	}

	object UploadPath {
		def unapply(path: List[String]) = path match {
			case "api" :: "project" :: method :: Nil => Some(method)
			case _ => None
		}
	}

	val askTimeout: Timeout = 5.minute
	val awaitTimeout: Timeout = Timeout(askTimeout.duration + askTimeout.duration / 2)
	implicit val timeout = askTimeout

	serve {
		case UploadPath("create") Post req => fallbackResponse {
			try {
				for {
					(inputFile, originalName, cleanup) <- getReqFile(req) ?~! "Creating a new project requires a file"
					_ <- Await.result((inputFileProcessor() ? CanProcessFile(inputFile)).mapTo[Boolean], awaitTimeout.duration) ?~ "The file you picked does not contain any supported input data. Refer to https://github.com/codedx/codepulse/wiki/user-guide for what an input file should contain."
					name <- req.param("name") ?~ "You must specify a name"
				} yield {

					val createProjectFuture = projectInput() ? (CreateProject(inputFile, (projectData, storage, eventBus) => {
						projectData.metadata.name = name
						projectData.metadata.creationDate = System.currentTimeMillis

						def post(): Unit = {
							val date = projectData.metadata.creationDate
							val createDate = System.currentTimeMillis
							if (createDate > date) {
								projectData.metadata.creationDate = createDate
							}
						}

						eventBus.publish(ProcessStatus.DataInputAvailable(projectData.id.num.toString, storage, projectData.treeNodeData, projectData.sourceData, post))
					}))

					val projectData = Await.result(createProjectFuture.mapTo[ProjectData], awaitTimeout.duration)
					projectManager.getProject(projectData.id).map(p => hrefResponse(p.id)).getOrElse(NotFoundResponse("Failed to process data input - unable to retrieve new project by ID"))
				}
			}
			catch  {
				case askTimeoutException: AskTimeoutException => {
					logAndMakeProjectFileUploadError(s"Request to create project did not complete within the allotted timeframe of ${askTimeout.duration.toSeconds} seconds", askTimeoutException)
				}
				case timeoutException: TimeoutException => {
					logAndMakeProjectFileUploadError(s"Waited ${awaitTimeout.duration.toSeconds} seconds before abandoning the request to create a new project", timeoutException)
				}
				case ex: Exception => {
					logAndMakeProjectFileUploadError(s"Failed to create a new project because of an unexpected error", ex)
				}
			}
		}

		case UploadPath("import") Post req => fallbackResponse {
			try {
				for {
					(inputFile, _, cleanup) <- getReqFile(req) ?~! "Importing a project requires a file"
					_ <- ProjectUploadData.checkForProjectExport(inputFile) ?~ {
						s"The file you picked isn't an exported project file."
					}
				} yield {
					val projectId = ProjectUploadData.handleProjectExport(inputFile, { cleanup() })

					hrefResponse(projectId)
				}
			} catch {
				case ex: Exception => {
					logAndMakeProjectFileUploadError(s"Failed to import project because of an unexpected error", ex)
				}
			}
		}
	}

	def getReqFile(req: Req): Box[(File, String, () => Unit)] = req.uploadedFiles match {
		case List(upfile: ManualOnDiskFileParamHolder) =>
			Full((upfile.localFile, upfile.fileName, upfile.delete))

		case Nil => req.param("path") flatMap { path =>
			val file = new File(path)
			if (file.canRead) Some((file, file.getName, () => ())) else None
		}

		case _ => Empty
	}

	implicit class BooleanBoxQuestion(bool: Boolean) {
		def ?~(msg: String) =
			if (bool) Full()
			else Failure(msg)
	}

	def hrefResponse(projectId: ProjectId) = {

		projectManager.getProject(projectId) match {
			case None =>
				// Something went horribly wrong. Somehow the ProjectManager
				// generated a ProjectId for us without actually registering it?
				// This is probably a race condition, but we still don't kwow
				// what is causing it. For now, just sleep for a bit and hope
				// that the race is over 1 second from now.
				Thread.sleep(1000)
			case Some(_) =>
			// of course it's a Some. It would be crazy if it wasn't.
		}

		val href = ProjectDetailsPage.projectHref(projectId)
		JsonResponse("href" -> href)
	}

	def fallbackResponse(box: Box[LiftResponse]) = box match {
		case Full(resp) => resp
		case Empty => NotFoundResponse("an unknown error occurred")
		case Failure(msg, _, _) => NotFoundResponse(msg)
	}
}
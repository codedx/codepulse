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
import net.liftweb.http.BadResponse
import net.liftweb.http.LiftResponse
import net.liftweb.http.LiftRules
import net.liftweb.http.NotFoundResponse
import net.liftweb.http.OkResponse
import net.liftweb.http.OnDiskFileParamHolder
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.common.Failure
import com.secdec.codepulse.pages.traces.ProjectDetailsPage
import net.liftweb.http.JsonResponse
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Box
import com.secdec.codepulse.data.model.ProjectId
import net.liftweb.http.PlainTextResponse

class ProjectFileUploadHandler(projectManager: ProjectManager) extends RestHelper {

	def initializeServer() = {
		LiftRules.statelessDispatch.append(this)
		this
	}

	object UploadPath {
		def unapply(path: List[String]) = path match {
			case "api" :: "project" :: method :: Nil => Some(method)
			case _ => None
		}
	}

	serve {
		case UploadPath("create") Post req => fallbackResponse {
			for {
				(inputFile, originalName) <- getReqFile(req) ?~! "Creating a new project requires a file"
				_ <- ProjectUploadData.checkForBinaryZip(inputFile) ?~ {
					s"The file you picked doesn't have any compiled Java files."
				}
				name <- req.param("name") ?~ "You must specify a name"
			} yield {
				val projectId = ProjectUploadData.handleBinaryZip(inputFile, originalName)

				// set the new trace's name
				projectManager.getProject(projectId) foreach {
					_.projectData.metadata.name = name
				}

				hrefResponse(projectId)
			}
		}

		case UploadPath("import") Post req => fallbackResponse {
			for {
				(inputFile, _) <- getReqFile(req) ?~! "Importing a project requires a file"
				_ <- ProjectUploadData.checkForProjectExport(inputFile) ?~ {
					s"The file you picked isn't an exported project file."
				}
			} yield {
				val projectId = ProjectUploadData.handleProjectExport(inputFile)

				hrefResponse(projectId)
			}
		}
	}

	def getReqFile(req: Req): Box[(File, String)] = req.uploadedFiles match {
		case List(upfile: OnDiskFileParamHolder) =>
			Full(upfile.localFile -> upfile.fileName)

		case Nil => req.param("path") flatMap { path =>
			val file = new File(path)
			if (file.canRead) Some(file -> file.getName) else None
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
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
import com.secdec.codepulse.pages.traces.TraceDetailsPage
import net.liftweb.http.JsonResponse
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Box
import com.secdec.codepulse.data.trace.TraceId

class TraceFileUploadHandler(traceManager: TraceManager) extends RestHelper {

	def initializeServer() = {
		LiftRules.statelessDispatch.append(this)
		this
	}

	object UploadPath {
		def unapply(path: List[String]) = path match {
			case "trace-api" :: "trace" :: method :: Nil => Some(method)
			case _ => None
		}
	}

	serve {
		case UploadPath("create") Post req =>
			for {
				inputFile <- getReqFile(req) ?~! "Creating a new trace requires a file"
				if TraceUploadData.checkForBinaryZip(inputFile)
				name <- req.param("name") ?~ "You must specify a name"
			} yield {
				val traceId = TraceUploadData.handleBinaryZip(inputFile)

				// set the new trace's name
				traceManager.getTrace(traceId) foreach {
					_.traceData.metadata.name = name
				}

				hrefResponse(traceId)
			}

		case UploadPath("import") Post req =>
			for {
				inputFile <- getReqFile(req) ?~! "Importing a trace requires a file"
				if TraceUploadData.checkForTraceExport(inputFile)
			} yield {
				val traceId = TraceUploadData.handleTraceExport(inputFile)

				hrefResponse(traceId)
			}
	}

	def getReqFile(req: Req): Box[File] = req.uploadedFiles match {
		case List(upfile: OnDiskFileParamHolder) =>
			Full(upfile.localFile)

		case Nil => req.param("path") flatMap { path =>
			val file = new File(path)
			if (file.canRead) Some(file) else None
		}

		case _ => Empty
	}

	def hrefResponse(traceId: TraceId) = {
		val href = TraceDetailsPage.traceHref(traceId)
		JsonResponse("href" -> href)
	}

}
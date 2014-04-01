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

class TraceFileUploadHandler(traceManager: TraceManager) extends RestHelper {

	def initializeServer() = {
		LiftRules.statelessDispatch.append(this)
		this
	}

	object UploadPath {
		def unapply(path: List[String]) = path match {
			case "trace-api" :: "file-upload" :: tail => Some(tail)
			case _ => None
		}
	}

	serve {
		case UploadPath(Nil) Post req => serveFileUpload(req)
	}

	def serveFileUpload(req: Req): LiftResponse = req.uploadedFiles match {
		case List(upfile: OnDiskFileParamHolder) =>
			serveFileUpload(upfile.localFile)

		case Nil => req.param("path").toOption match {
			case None => BadResponse()
			case Some(path) =>
				val file = new File(path)
				println(s"Not actually uploading: analyzing file $file")
				serveFileUpload(file)
		}
	}

	def serveFileUpload(upfile: File): LiftResponse = {
		println(s"Uploaded file ${upfile.getName}")

		val uploadedTraceId = TraceUploadData.handleUpload(upfile)
		println(s"Upload resulted in trace: $uploadedTraceId")

		uploadedTraceId match {
			case Full(traceId) =>
				val target = traceManager.getTrace(traceId)
				val href = TraceDetailsPage.traceHref(traceId)
				JsonResponse("href" -> href)
			case Empty =>
				NotFoundResponse()
			case Failure(msg, _, _) =>
				NotFoundResponse(msg)
		}

	}

}
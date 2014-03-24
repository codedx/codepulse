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

package bootstrap.liftweb

import java.io.File
import java.io.IOException

import scala.util.control.Exception.catching

import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import net.liftweb.common.Loggable
import net.liftweb.http.JsonResponse
import net.liftweb.http.LiftRules
import net.liftweb.http.LiftRulesMocker.toLiftRules
import net.liftweb.http.OnDiskFileParamHolder
import net.liftweb.json.JsonDSL.pair2jvalue
import net.liftweb.json.JsonDSL.string2jvalue

object FileUploadSetup extends Loggable {

	val MaxFileSize = 200 * 1024 * 1024

	def init(liftRules: LiftRules) = {
		setupMimeHandler(liftRules)
		setupUploadProgress(liftRules)
		// FileUtils.forceMkdir(tempFileDir)
	}

	def createTempUpload: File = {
		val tempFile = File.createTempFile("upload", ".data")
		tempFile.deleteOnExit
		tempFile
	}

	def setupMimeHandler(liftRules: LiftRules) = {

		liftRules.maxMimeFileSize = MaxFileSize
		liftRules.maxMimeSize = MaxFileSize

		// Send uploaded files to a temp folder instead of putting them in memory
		liftRules.handleMimeFile = (fieldName, contentType, fileName, stream) => {
			/** The session is different between browsers. If you open up chrome and
			  * firefox and browse to the same page, the session will be different
			  * between them. As a counterpoint, 2 separate instances of chrome on
			  * the same computer (e.g. 2 tabs) will connect to the same session, from
			  * what I've seen.
			  * ~Dylan
			  */
			//println("session id (boxed) is " + S.session.map { s => s.uniqueId })

			val uploadFile = {
				import scala.util.control.Exception._
				//copy the stream to a local temp file:
				val tempFile = createTempUpload
				val output = catching(classOf[java.io.IOException]) opt { FileUtils.openOutputStream(tempFile) }
				for (o <- output) {
					try {
						IOUtils.copy(stream, o)
					} catch {
						case e: Exception => logger.error("Exception while handling File Upload", e)
					} finally {
						IOUtils.closeQuietly(o)
						IOUtils.closeQuietly(stream)
					}
				}
				tempFile
			}

			logger.debug("handleMimeFile(%s, %s, %s)".format(fieldName, contentType, fileName))
			val result = new OnDiskFileParamHolder(fieldName, contentType, fileName, uploadFile)
			/** The OnDiskFile.. goes to a temp file in your temp folder.
			  */

			result
		}

		liftRules.exceptionHandler.prepend {
			case (mode, req, exc: SizeLimitExceededException) => {
				logger.error(exc)

				val limit = liftRules.maxMimeFileSize / 1024 / 1024
				JsonResponse(("error" -> s"The file is too large (the limit is ${limit} MB)."), List.empty, List.empty, 500)
			}
		}
	}

	def setupUploadProgress(liftRules: LiftRules) = {
		// this is called while uploading files
		liftRules.progressListener = {
			val original = LiftRules.progressListener
			val ret: (Long, Long, Int) => Unit =
				(numRead, numTotal, pItems) =>
					{
						//println("progress Listener " + numRead + " / " + numTotal)
						original(numRead, numTotal, pItems)
					}
			ret

			/** Default implementation just does nothing. If we want to start
			  * streaming uploads and get progress bars, we should leverage this
			  * function, as well as the S.session variable.
			  */
			//println("Read %d / %d bytes on item %d".format(numRead, numTotal, pItems))
		}

	}
}
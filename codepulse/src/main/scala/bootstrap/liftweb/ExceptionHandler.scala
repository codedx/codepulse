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

import java.sql.SQLException

import scala.xml.NodeSeq

import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.http.LiftRules
import net.liftweb.http.PlainTextResponse
import net.liftweb.http.S
import net.liftweb.http.XmlResponse

/** Logs unhandled exceptions and displays a friendly error message.
  * @author ChrisE
  */
object ExceptionHandler extends Loggable {

	def init(liftRules: LiftRules) = {
		liftRules.exceptionHandler.prepend {
			case (mode, req, exc) => {

				logger.error("Unhandled exception.", exc)

				S.containerRequest match {
					case Full(httpReq) => XmlResponse(S.render(httpError(exc), httpReq).head, 500)
					case _ => PlainTextResponse(s"${errorMessage(exc)} ${advice}", 500)
				}
			}
		}
	}

	private def errorMessage(throwable: Throwable) = {
		throwable match {
			case exc: SQLException => "A database error occurred."
			case exc => s"An internal server error occurred."
		}
	}

	private val advice = "Consult the log file for further details."

	private def httpError(exc: Throwable): NodeSeq = {
		val response = <lift:surround with="barebones" at="content">
			<div class="titlearea">
				<div class="wscope">
					<h1>Error</h1>
				</div>
			</div>
			<div class="wscope">
				<div class="two-thirds">
					<p>{ errorMessage(exc) }</p>
					<p>{ advice }</p>
				</div>
			</div>
		</lift:surround>

		response.head
	}

}
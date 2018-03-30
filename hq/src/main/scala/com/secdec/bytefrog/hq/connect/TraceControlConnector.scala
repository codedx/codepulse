/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.codedx.codepulse.hq.connect

import java.util.concurrent.LinkedBlockingQueue

import scala.concurrent.Future
import scala.concurrent.Promise

import com.codedx.codepulse.agent.common.connect.Connection
import com.codedx.codepulse.hq.protocol.ControlMessage
import com.codedx.codepulse.hq.protocol.ControlMessageSender

object TraceControlConnector extends TraceControlConnector

/** An object that ties Control Connections to requests for new ControlConnections.
  */
trait TraceControlConnector {

	private val awaitingConnectionsQueue =
		new LinkedBlockingQueue[(() => ControlMessage.Configuration[_], Promise[ControlConnection])]

	/** Get a Control connection as a Future, given an agent configuration to assign to that connection.
	  */
	def getControlConnection(configMessage: () => ControlMessage.Configuration[_]): Future[ControlConnection] = {
		val p = Promise[ControlConnection]
		awaitingConnectionsQueue put configMessage -> p
		p.future
	}

	def addControlConnection(cc: ControlConnection): Option[ControlMessage.Configuration[_]] = {
		// check the connections callback queue.
		// `.poll` will return null immediately if nothing is available
		Option { awaitingConnectionsQueue.poll } match {

			case None => None

			case Some((config, promise)) =>
				//fulfill the promise to the tracer
				if (promise.trySuccess(cc)) {
					//return the tracer's configuration
					Some(config())
				} else {
					None
				}

		}
	}
}
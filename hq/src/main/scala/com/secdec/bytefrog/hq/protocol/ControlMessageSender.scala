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

package com.codedx.codepulse.hq.protocol

import java.io.DataOutputStream

import com.codedx.codepulse.agent.common.connect.Connection

/** A Trait that describes an object that can send ControlMessages to a connection.
  *
  * One of the motivating purposes for this trait is to hide the fact that messages
  * might be buffered in the underlying stream, so that callers don't need to know
  * that they would normally need to explicitly `flush` the stream.
  */
trait ControlMessageSender {
	/** Send any number of control messages via the given `connection`, ensuring
	  * synchronized access to the connection's output stream, and making sure
	  * to flush the stream afterward.
	  */
	def sendMessages(connection: Connection)(messages: ControlMessage*): Unit = {
		val out = connection.output
		out.synchronized {
			try {
				//write each message
				for (m <- messages) writeMessage(out, m)
			} finally {
				//flush the stream: control messages shouldn't need to wait for any buffering
				out.flush
			}
		}
	}

	/** Write an individual message to the given `out` stream. This method must be implemented
	  * by concrete implementations of `ControlMessageSender`, and will generally have a close
	  * coupling with a specific [[MessageProtocol]].
	  * This method is called from within [[sendMessages]].
	  */
	protected def writeMessage(out: DataOutputStream, message: ControlMessage): Unit
}
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

import com.codedx.codepulse.agent.common.connect.Connection
import com.codedx.codepulse.hq.protocol.DataMessageHandler
import com.codedx.codepulse.hq.protocol.DataMessageParser

/** Represents a connection to an Agent that will be used for receiving
  * incoming data messages from that Agent. The underlying connection
  * and protocols are hidden from clients, so that they should never need
  * to interact with Streams, Sockets, or MessageProtocols.
  *
  * @param connection the underlying [[Connection]] to be used
  * @param eventReader a [[DataEventReader]] that will be used to parse incoming data events
  */
class DataConnection(connection: Connection, parser: DataMessageParser) {

	/** Closes the underlying connection.
	  * After closing, calls to `readEvent` are expected to fail, though
	  * in the case of buffered streams, there is no guarantee of failure.
	  */
	def close: Unit = {
		connection.close
	}

	/** Reads events from the stream using the given DataMessageHandler.
	  * This method will block the thread it is called in until the
	  * connection is closed or reaches EOF.
	  */
	def readEvents(handler: DataMessageHandler): Unit = {
		parser.parse(connection.input, handler)
	}
}
/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
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

package com.codedx.codepulse.hq.connect

import com.codedx.codepulse.agent.common.connect.Connection
import com.codedx.codepulse.hq.protocol._

/** Represents a connection between HQ and an Agent, to be used for sending
  * and receiving control messages. The underlying connection and control
  * message sender/receivers are hidden from clients, so that they never need
  * to deal with any actual message sending implementation logic.
  *
  * A `protocolVersion` however, is exposed, as it is needed to allow a
  * Trace and its incoming DataConnections to know which underlying
  * protocols to use.
  *
  * @param protocolVersion The version number of the underlying message protocols.
  * @param connection The underlying [[Connection]] to be used
  * @param messageReader A [[ControlMessageReader]] that is used to receive incoming control messages
  * @param messageSender A [[ControlMessageSender]] that is used to send outgoing control messages
  */
class ControlConnection(
	val protocolVersion: Int,
	connection: Connection,
	messageReader: ControlMessageReader,
	messageSender: ControlMessageSender) {

	/** Closes the connection between HQ and the Agent.
	  * After closing, send and receive operations will generally be expected to fail.
	  */
	def close: Unit = {
		connection.close
	}

	/** Send any number of ControlMessages over the connection.
	  * This method uses "varargs", so it can be called as {{{
	  * 	send(Start, Stop, Pause)
	  * 	//or
	  * 	send(Seq(Start, Stop, Pause): _*)
	  * }}}
	  *
	  * @param messages the messages to be sent
	  */
	def send(messages: ControlMessage*): Unit = {
		messageSender.sendMessages(connection)(messages: _*)
	}

	/** Reads a single ControlMessage from the incoming part of the connection
	  * @return the message that was read
	  */
	def recieve(): ControlMessage = {
		val in = connection.input
		in.synchronized {
			messageReader.readMessage(in)
		}
	}

}
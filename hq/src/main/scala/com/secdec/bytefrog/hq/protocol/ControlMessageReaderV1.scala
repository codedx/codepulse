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

package com.codedx.codepulse.hq.protocol

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.SocketException

import com.codedx.codepulse.agent.common.message.AgentOperationMode
import com.codedx.codepulse.agent.common.message.MessageConstantsV1

/** Convenient singleton instance of the ControlMessageReaderV1 class.
  * This is okay to do because the ControlMessageReaderV1 class doesn't
  * keep any internal state, so it is thread-safe to reuse in order to
  * read from multiple streams.
  */
object ControlMessageReaderV1 extends ControlMessageReaderV1

class ControlMessageReaderV1 extends ControlMessageReader {
	private object HeartbeatOperationMode {
		val Initializing: Byte = 'I'
		val Tracing: Byte = 'T'
		val Paused: Byte = 'P'
		val Suspended: Byte = 'S'
		val Shutdown: Byte = 'X'

		def unapply(mode: Byte): Option[AgentOperationMode] = mode match {
			case Initializing => Some(AgentOperationMode.Initializing)
			case Tracing => Some(AgentOperationMode.Tracing)
			case Paused => Some(AgentOperationMode.Paused)
			case Suspended => Some(AgentOperationMode.Suspended)
			case Shutdown => Some(AgentOperationMode.Shutdown)
			case _ => None
		}
	}

	def readMessage(stream: DataInputStream): ControlMessage = try {
		stream.readByte match {
			case MessageConstantsV1.MsgError => ControlMessage.Error(stream.readUTF)
			case MessageConstantsV1.MsgHeartbeat => ControlMessage.Heartbeat(
				stream.readByte match {
					case HeartbeatOperationMode(mode) => mode
					case _ => throw new IllegalArgumentException("unknown heartbeat operation mode")
				}, stream.readUnsignedShort)
			case MessageConstantsV1.MsgClassTransformed => ControlMessage.ClassTransformed(stream.readUTF)
			case MessageConstantsV1.MsgClassTransformFailed => ControlMessage.ClassTransformFailed(stream.readUTF)
			case MessageConstantsV1.MsgClassIgnored => ControlMessage.ClassIgnored(stream.readUTF)
			case MessageConstantsV1.MsgDataBreak => ControlMessage.DataBreak(stream.readInt)
			case _ => ControlMessage.Unknown
		}
	} catch {
		case e: EOFException => ControlMessage.EOF
		case e: IOException if e.getMessage equalsIgnoreCase "stream closed" => ControlMessage.EOF
		case e: SocketException if e.getMessage equalsIgnoreCase "connection reset" => ControlMessage.EOF
		case e: IOException => ControlMessage.Unknown
	}
}
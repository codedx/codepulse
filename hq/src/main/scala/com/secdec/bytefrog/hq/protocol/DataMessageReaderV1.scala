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

import com.codedx.codepulse.agent.common.message.MessageConstantsV1._
import IO.{ Input, Data, EOF, Error }

object DataMessageReaderV1 extends DataMessageReaderV1

class DataMessageReaderV1 extends DataMessageReader {

	def readMessage(stream: DataInputStream): Input[DataMessage] = {
		try {
			//read in the "message type id" byte
			val typeId = stream.readByte

			typeId match {
				case MsgMapThreadName => Data { readMapThreadName(stream) }
				case MsgMapMethodSignature => Data { readMapMethodSignature(stream) }
				case MsgMapException => Data { readMapException(stream) }
				case MsgMethodEntry => Data { readMethodEntry(stream) }
				case MsgMethodExit => Data { readMethodExit(stream) }
				case MsgException => Data { readException(stream) }
				case MsgExceptionBubble => Data { readExceptionBubble(stream) }
				case MsgMarker => Data { readMarker(stream) }
				case _ => Error {
					new IOException(s"Unexpected message type id: $typeId")
				}
			}
		} catch {
			case e: EOFException => EOF
			case e: IOException => Error(e)
		}
	}

	protected def readMapThreadName(stream: DataInputStream) = {
		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[2 bytes: length of encoded thread name][n bytes: encoded thread name]
		val threadName = stream.readUTF

		DataMessage.UnsequencedData(DataMessageContent.MapThreadName(threadName, threadId, timestamp))
	}

	protected def readMapMethodSignature(stream: DataInputStream) = {
		//[4 bytes: assigned signature ID]
		val methodId = stream.readInt

		//[2 bytes: length of encoded signature][n bytes: encoded signature]
		val methodSig = stream.readUTF

		DataMessage.UnsequencedData(DataMessageContent.MapMethodSignature(methodSig, methodId))
	}

	protected def readMapException(stream: DataInputStream) = {
		//[4 bytes: assigned exception ID]
		val exceptionId = stream.readInt

		//[2 bytes: length of encoded exception][n bytes: encoded exception]
		val exception = stream.readUTF

		DataMessage.UnsequencedData(DataMessageContent.MapException(exception, exceptionId))
	}

	protected def readMethodEntry(stream: DataInputStream) = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequenceId = stream.readInt

		//[4 bytes: method signature ID]
		val methodId = stream.readInt

		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		DataMessage.SequencedData(
			timestamp, sequenceId,
			DataMessageContent.MethodEntry(methodId, timestamp, threadId))
	}

	protected def readMethodExit(stream: DataInputStream) = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequenceId = stream.readInt

		//[4 bytes: method signature ID]
		val methodId = stream.readInt

		//[2 bytes: line number]
		val lineNum = stream.readUnsignedShort

		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		DataMessage.SequencedData(
			timestamp, sequenceId,
			DataMessageContent.MethodExit(methodId, timestamp, lineNum, threadId))
	}

	protected def readException(stream: DataInputStream) = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequenceId = stream.readInt

		//[4 bytes: method signature ID]
		val methodId = stream.readInt

		//[4 bytes: exceptionId]
		val exceptionId = stream.readInt

		//[2 bytes: line number]
		val lineNum = stream.readUnsignedShort

		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		DataMessage.SequencedData(
			timestamp, sequenceId,
			DataMessageContent.Exception(exceptionId, methodId, timestamp, lineNum, threadId))
	}

	protected def readExceptionBubble(stream: DataInputStream) = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequenceId = stream.readInt

		//[4 bytes: method signature ID]
		val methodId = stream.readInt

		//[4 bytes: exceptionId]
		val exceptionId = stream.readInt

		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		DataMessage.SequencedData(
			timestamp, sequenceId,
			DataMessageContent.ExceptionBubble(exceptionId, methodId, timestamp, threadId))
	}

	protected def readMarker(stream: DataInputStream) = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequence = stream.readInt

		//[2 + N bytes: "key" string]
		val key = stream.readUTF

		//[2 + N bytes: "value" string]
		val value = stream.readUTF

		DataMessage.SequencedData(
			timestamp, sequence,
			DataMessageContent.Marker(key, value, timestamp))
	}
}
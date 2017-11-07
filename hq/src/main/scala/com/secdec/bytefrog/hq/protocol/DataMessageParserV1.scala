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

package com.secdec.bytefrog.hq.protocol

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException

import com.secdec.bytefrog.common.message.MessageConstantsV1._
import IO.{ Input, Data, EOF, Error }

/** Convenient singleton version of the DataMessageParserV1 class */
object DataMessageParserV1 extends DataMessageParserV1

/** A DataMessageParser implementation that assumes the data in each input stream
  * was put there by a MessageProtocol Version 1 implementation.
  *
  * This implementation is thread-safe. DataMessageParserV1 keeps no
  * internal state, so it should have no problem calling `parse`
  * on several different input streams at once, provided each
  * `handler` will not have its own concurrency issues.
  */
class DataMessageParserV1 extends DataMessageParser {

	def parse(data: DataInputStream, handler: DataMessageHandler, progressHandler: Long => Unit, parseDataBreaks: Boolean): Unit = {
		var readBytes = 0L

		try {
			//read forever, breaking out of the loop via EOF or IOExceptions
			while (true) {
				readBytes += readMessage(data, handler, parseDataBreaks)
				progressHandler(readBytes)
			}
		} catch {
			case e: EOFException => handler.handleParserEOF
			case e: IOException => handler.handleParserError(e)
		}
	}

	/** Read an individual message from the `stream`, delegating to the
	  * `handler` for callbacks for each message type. This method will
	  * be called many times by `parse`.
	  */
	def readMessage(stream: DataInputStream, handler: DataMessageHandler, parseDataBreaks: Boolean): Int = {
		//read in the "message type id" byte
		val typeId = stream.readByte

		// we read the message type byte, plus whatever the delegate reads
		(typeId match {
			case MsgMapThreadName => readMapThreadName(stream, handler)
			case MsgMapMethodSignature => readMapMethodSignature(stream, handler)
			case MsgMapException => readMapException(stream, handler)
			case MsgMethodEntry => readMethodEntry(stream, handler)
			case MsgMethodExit => readMethodExit(stream, handler)
			case MsgException => readException(stream, handler)
			case MsgExceptionBubble => readExceptionBubble(stream, handler)
			case MsgMarker => readMarker(stream, handler)
			case MsgDataBreak if parseDataBreaks => readDataBreak(stream, handler)
			case _ => throw new IOException(s"Unexpected message type id: $typeId")
		}) + 1
	}

	protected def readMapThreadName(stream: DataInputStream, handler: DataMessageHandler): Int = {
		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[2 bytes: length of encoded thread name][n bytes: encoded thread name]
		stream mark 2
		val threadNameLen = stream.readUnsignedShort()
		stream.reset
		val threadName = stream.readUTF

		handler.handleMapThreadName(threadName, threadId, timestamp)

		// read 8 bytes, plus thread name length
		8 + threadNameLen
	}

	protected def readMapMethodSignature(stream: DataInputStream, handler: DataMessageHandler): Int = {
		//[4 bytes: assigned signature ID]
		val methodId = stream.readInt

		//[2 bytes: length of encoded signature][n bytes: encoded signature]
		stream mark 2
		val methodSigLen = stream.readUnsignedShort()
		stream.reset
		val methodSig = stream.readUTF

		handler.handleMapMethodSignature(methodSig, methodId)

		// read 6 bytes, plus method signature length
		6 + methodSigLen
	}

	protected def readMapException(stream: DataInputStream, handler: DataMessageHandler): Int = {
		//[4 bytes: assigned exception ID]
		val exceptionId = stream.readInt

		//[2 bytes: length of encoded exception][n bytes: encoded exception]
		stream mark 2
		val exceptionLen = stream.readUnsignedShort()
		stream.reset
		val exception = stream.readUTF

		handler.handleMapException(exception, exceptionId)

		// read 6 bytes, plus exception length
		6 + exceptionLen
	}

	protected def readMethodEntry(stream: DataInputStream, handler: DataMessageHandler): Int = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequenceId = stream.readInt

		//[4 bytes: method signature ID]
		val methodId = stream.readInt

		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		handler.handleMethodEntry(methodId, timestamp, sequenceId, threadId)

		// read 14 bytes
		14
	}

	protected def readMethodExit(stream: DataInputStream, handler: DataMessageHandler): Int = {
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

		handler.handleMethodExit(methodId, timestamp, sequenceId, lineNum, threadId)

		// read 14 bytes
		14
	}

	protected def readException(stream: DataInputStream, handler: DataMessageHandler): Int = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequenceId = stream.readInt

		//[4 bytes: method signature ID]
		val methodId = stream.readInt

		//[4 bytes: exception ID]
		val exceptionId = stream.readInt

		//[2 bytes: line number]
		val lineNum = stream.readUnsignedShort

		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		handler.handleExceptionMessage(exceptionId, methodId, timestamp, sequenceId, lineNum, threadId)

		// read 18 bytes, plus length of exception
		18
	}

	protected def readExceptionBubble(stream: DataInputStream, handler: DataMessageHandler): Int = {
		//[4 bytes: relative timestamp]
		val timestamp = stream.readInt

		//[4 bytes: current sequence]
		val sequenceId = stream.readInt

		//[4 bytes: method signature ID]
		val methodId = stream.readInt

		//[4 bytes: exception ID]
		val exceptionId = stream.readInt

		//[2 bytes: thread ID]
		val threadId = stream.readUnsignedShort

		handler.handleExceptionBubble(exceptionId, methodId, timestamp, sequenceId, threadId)

		// read 16 bytes
		16
	}

	protected def readMarker(stream: DataInputStream, handler: DataMessageHandler): Int = {
		// 8 bytes for timestamp + sequence
		val timestamp = stream.readInt
		val sequence = stream.readInt

		val key = stream.readUTF
		val value = stream.readUTF

		handler.handleMarkerMessage(timestamp, sequence, key, value)

		// 8 bytes + (2 + key.length) + (2 + value.length)
		12 + key.length + value.length
	}

	protected def readDataBreak(stream: DataInputStream, handler: DataMessageHandler): Int = {
		val sequenceId = stream.readInt
		handler.handleDataBreak

		4
	}
}
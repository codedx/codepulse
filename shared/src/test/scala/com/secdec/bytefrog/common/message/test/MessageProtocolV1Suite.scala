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

package com.secdec.bytefrog.common.message.test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter

import com.codedx.codepulse.agent.common.message._

class MessageProtocolV1Suite extends FunSuite with BeforeAndAfter {

	val protocol: MessageProtocol = new MessageProtocolV1
	val byteBuffer = new ByteArrayOutputStream
	val dataOutputStream = new DataOutputStream(byteBuffer)

	after {
		dataOutputStream.flush
		byteBuffer.reset
	}

	test("protocol version should be 1") {
		val result = protocol.protocolVersion

		assert(result == 1, "protocol version should be 1")
	}

	test("writeHello should write a valid hello message") {
		protocol.writeHello(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 2, "message should be 2 bytes long")
		assert(result(0) == 0, "message type ID should be 0")
		assert(result(1) == 1, "version should be 1")
	}

	test("writeDataHello should write a valid data hello message") {
		val runID: Byte = 5

		protocol.writeDataHello(dataOutputStream, runID)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 2, "message should be 2 bytes long")
		assert(result(0) == 30, "message type ID should be 30")
		assert(result(1) == runID, "run ID should contain given value")
	}

	test("writeError should write a valid error report message") {
		val errorMessage = "error message"

		protocol.writeError(dataOutputStream, errorMessage)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length > 3, "message should be larger than 3 bytes")
		assert(result(0) == 99, "message type ID should be 99")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		stream.mark(2)
		val errorLen = stream.readShort
		stream.reset
		val errorMessageResult = stream.readUTF

		assert(result.length == 3 + errorLen, "message should be 3 + N bytes long")
		assert(errorMessageResult == errorMessage, "error message should contain given value")
	}

	test("writeConfiguration should write a valid configuration message") {
		val configuration = Array[Byte](0x12, 0x23, 0x34)

		protocol.writeConfiguration(dataOutputStream, configuration)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 5 + configuration.length, "message should 5 + N bytes long")
		assert(result(0) == 1, "message type ID should be 1")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val configLen = stream.readInt
		val configurationResult = new Array[Byte](configLen)
		stream.read(configurationResult, 0, configLen)

		assert(configLen == configuration.length, "configuration length should correspond with given configuration")
		assert(configurationResult.sameElements(configuration), "configuration message should contain given value")
	}

	test("writeDataHelloReply should write a valid data hello reply message") {
		protocol.writeDataHelloReply(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 1, "message should be 1 byte long")
		assert(result(0) == 31, "message type ID should be 31")
	}

	test("writeStart should write a valid start message") {
		protocol.writeStart(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 1, "message should be 1 byte long")
		assert(result(0) == 2, "message type ID should be 2")
	}

	test("writeStop should write a valid stop message") {
		protocol.writeStop(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 1, "message should be 1 byte long")
		assert(result(0) == 3, "message type ID should be 3")
	}

	test("writePause should write a valid pause message") {
		protocol.writePause(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 1, "message should be 1 byte long")
		assert(result(0) == 4, "message type ID should be 4")
	}

	test("writeUnpause should write a valid unpause message") {
		protocol.writeUnpause(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 1, "message should be 1 byte long")
		assert(result(0) == 5, "message type ID should be 5")
	}

	test("writeSuspend should write a valid suspend message") {
		protocol.writeSuspend(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 1, "message should be 1 byte long")
		assert(result(0) == 6, "message type ID should be 6")
	}

	test("writeUnsuspend should write a valid unsuspend message") {
		protocol.writeUnsuspend(dataOutputStream)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 1, "message should be 1 byte long")
		assert(result(0) == 7, "message type ID should be 7")
	}

	test("writeClassTransformed should write a valid message") {
		protocol.writeClassTransformed(dataOutputStream, "Foo")
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 6)
		assert(result(0) == 40)
	}

	test("writeClassTransformFailed should write a valid message") {
		protocol.writeClassTransformFailed(dataOutputStream, "Foo")
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 6)
		assert(result(0) == 42)
	}

	test("writeClassIgnored should write a valid message") {
		protocol.writeClassIgnored(dataOutputStream, "Bar")
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 6)
		assert(result(0) == 41)
	}

	for (
		heartbeatMode <- Array(
			(AgentOperationMode.Initializing, 'I'),
			(AgentOperationMode.Tracing, 'T'),
			(AgentOperationMode.Paused, 'P'),
			(AgentOperationMode.Suspended, 'S'),
			(AgentOperationMode.Shutdown, 'X'))
	) test(s"writeHeartbeat should write a valid heartbeat message [${heartbeatMode._1.name()}]") {
		val sendQueueSize = 532

		protocol.writeHeartbeat(dataOutputStream, heartbeatMode._1, sendQueueSize)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 4, "message should be 4 bytes long")
		assert(result(0) == 8, "message type ID should be 8")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val heartbeatModeResult = stream.readByte
		val sendQueueSizeResult = stream.readShort

		assert(heartbeatModeResult == heartbeatMode._2, s"mode should be ${heartbeatMode._2}")
		assert(sendQueueSizeResult == sendQueueSize, "queue size should contain given value")
	}

	test("writeDataBreak should write a valid data break message") {
		val seq = 1234

		protocol.writeDataBreak(dataOutputStream, seq)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 5, "message should be 3 bytes long")
		assert(result(0) == 9, "message type ID should be 9")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val seqResult = stream.readInt

		assert(seqResult == seq, s"seq should be $seq")
	}

	test("writeMapThreadName should write a valid map thread name message") {
		val threadID = 23
		val relTime = 837
		val threadName = "test thread"

		protocol.writeMapThreadName(dataOutputStream, threadID, relTime, threadName)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length > 9, "message should be larger than 9 bytes")
		assert(result(0) == 10, "message type ID should be 10")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val threadIDResult = stream.readShort
		val relTimeResult = stream.readInt
		stream.mark(2)
		val threadNameLen = stream.readShort
		stream.reset
		val threadNameResult = stream.readUTF

		assert(result.length == 9 + threadNameLen, "message should be 9 + N bytes long")
		assert(threadIDResult == threadID, "thread ID should contain given value")
		assert(relTimeResult == relTime, "relative timestamp should contain given value")
		assert(threadNameResult == threadName, "thread name should contain given value")
	}

	test("writeMapMethodSignature should write a valid map method signature message") {
		val signatureID = 32847334
		val signature = "test thread"

		protocol.writeMapMethodSignature(dataOutputStream, signatureID, signature)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length > 7, "message should be larger than 7 bytes")
		assert(result(0) == 11, "message type ID should be 11")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val signatureIDResult = stream.readInt
		stream.mark(2)
		val signatureLen = stream.readShort
		stream.reset
		val signatureResult = stream.readUTF

		assert(result.length == 7 + signatureLen, "message should be 7 + N bytes long")
		assert(signatureIDResult == signatureID, "signature ID should contain given value")
		assert(signatureResult == signature, "signature should contain given value")
	}

	test("writeMapException should write a valid map exception message") {
		val exceptionID = 634
		val exception = "this program stinks"

		protocol.writeMapException(dataOutputStream, exceptionID, exception)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length > 7, "message should be larger than 7 bytes")
		assert(result(0) == 12, "message type ID should be 12")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val exceptionIDResult = stream.readInt
		stream.mark(2)
		val exceptionLen = stream.readShort
		stream.reset
		val exceptionResult = stream.readUTF

		assert(result.length == 7 + exceptionLen, "message should be 7 + N bytes long")
		assert(exceptionIDResult == exceptionID, "exception ID should contain given value")
		assert(exceptionResult == exception, "exception should contain given value")
	}

	test("writeMethodEntry should write a valid method entry message") {
		val relTime = 376433
		val sequence: Short = 372
		val signatureID = 785932
		val threadID = 13

		protocol.writeMethodEntry(dataOutputStream, relTime, sequence, signatureID, threadID)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 15, "message should be 15 bytes long")
		assert(result(0) == 20, "message type ID should be 20")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val relTimeResult = stream.readInt
		val sequenceResult = stream.readInt
		val signatureIDResult = stream.readInt
		val threadIDResult = stream.readShort

		assert(relTimeResult == relTime, "relative timestamp should contain given value")
		assert(sequenceResult == sequence, "sequence should contain given value")
		assert(signatureIDResult == signatureID, "signature ID should contain given value")
		assert(threadIDResult == threadID, "thread ID should contain given value")
	}

	test("writeMethodExit should write a valid method exit message") {
		val relTime = 376433
		val sequence: Short = 372
		val signatureID = 785932
		val exThrown = false
		val threadID = 13

		protocol.writeMethodExit(dataOutputStream, relTime, sequence, signatureID, exThrown, threadID)
		dataOutputStream.flush
		val result = byteBuffer.toByteArray

		assert(result.length == 16, "message should be 17 bytes long")
		assert(result(0) == 21, "message type ID should be 21")

		val stream = new DataInputStream(new ByteArrayInputStream(result))
		stream.skipBytes(1)
		val relTimeResult = stream.readInt
		val sequenceResult = stream.readInt
		val signatureIDResult = stream.readInt
		val exThrownResult = stream.readBoolean
		val threadIDResult = stream.readShort

		assert(relTimeResult == relTime, "relative timestamp should contain given value")
		assert(sequenceResult == sequence, "sequence should contain given value")
		assert(signatureIDResult == signatureID, "signature ID should contain given value")
		assert(exThrownResult == exThrown, "line number should contain given value")
		assert(threadIDResult == threadID, "thread ID should contain given value")
	}
}
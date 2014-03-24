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

package com.secdec.bytefrog.hq.testutil

import java.io.DataOutputStream

import org.scalamock.MockFunction1
import org.scalamock.scalatest.MockFactory

import com.secdec.bytefrog.common.connect.Connection
import com.secdec.bytefrog.common.message.MessageProtocol
import com.secdec.bytefrog.hq.connect.ControlConnection
import com.secdec.bytefrog.hq.protocol._

trait MockedSendingHelpers { self: MockFactory =>

	class MockedSendingConnection(sender: ControlMessageSender, writer: MockFunction1[ControlMessage, Unit])
		extends ControlConnection(1, null, null, sender) {
		val writeMsg = writer
	}

	class MockedMessageSender extends ControlMessageSender {
		val writeMsg = mockFunction[ControlMessage, Unit]
		override def sendMessages(connection: Connection)(messages: ControlMessage*) = {
			for (msg <- messages) writeMsg(msg)
		}
		def writeMessage(out: DataOutputStream, message: ControlMessage) = writeMsg(message)
	}

	protected def mockControlConnection = {
		val writeFunc = mockFunction[ControlMessage, Unit]
		val sender = new ControlMessageSender {
			override def sendMessages(connection: Connection)(messages: ControlMessage*) = {
				for (msg <- messages) writeFunc(msg)
			}
			def writeMessage(out: DataOutputStream, message: ControlMessage) = ???
		}
		new MockedSendingConnection(sender, writeFunc)
	}

	class MockedProtocolHelper(val latestProtocolVersion: Int = 1) extends ProtocolHelper {
		val controlReader = mock[ControlMessageReader]
		val controlWriter = mock[ControlMessageSender]
		val dataReader = mock[DataEventReader]
		val protocol = mock[MessageProtocol]
		val dataMsgReader = mock[DataMessageReader]
		val dataMsgParser = mock[DataMessageParser]

		def getControlMessageReader(pv: Int) = Some(controlReader)
		def getControlMessageSender(pv: Int) = Some(controlWriter)
		def getDataEventReader(pv: Int) = Some(dataReader)
		def getMessageProtocol(pv: Int) = Some(protocol)
		def getDataMessageReader(pv: Int) = Some(dataMsgReader)
		def getDataMessageParser(pv: Int) = Some(dataMsgParser)
	}
}
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

package com.codedx.codepulse.hq.testutil

import java.io.DataOutputStream

import org.scalamock.function.MockFunction1
import org.scalamock.scalatest.MockFactory

import com.codedx.codepulse.agent.common.connect.Connection
import com.codedx.codepulse.agent.common.message.MessageProtocol
import com.codedx.codepulse.agent.common.message.MessageProtocolV2
import com.codedx.codepulse.hq.connect.ControlConnection
import com.codedx.codepulse.hq.protocol._

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

	class MockedMessageProtocolV2 extends MessageProtocolV2 {
		val writeConfigJson = mockFunction[String, Unit]
		override def writeConfiguration(out: DataOutputStream, configJson: String) = {
			writeConfigJson(configJson)
		}
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
		val protocol = mock[MessageProtocol]
		val dataMsgParser = mock[DataMessageParser]

		def getControlMessageReader(pv: Int) = Some(controlReader)
		def getControlMessageSender(pv: Int) = Some(controlWriter)
		def getMessageProtocol(pv: Int) = Some(protocol)
		def getDataMessageParser(pv: Int) = Some(dataMsgParser)
	}
}
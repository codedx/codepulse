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

package com.secdec.bytefrog.agent.init.test

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.DataInputStream
import java.io.ObjectOutputStream

import collection.JavaConversions._

import org.scalatest._
import org.scalatest.Matchers._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.concurrent.Conductors
import org.scalamock.scalatest.MockFactory

import com.codedx.codepulse.agent.errors.ErrorHandler
import com.codedx.codepulse.agent.errors.ErrorListener
import com.codedx.codepulse.agent.init.ControlConnectionHandshakeV1
import com.codedx.codepulse.agent.protocol.ProtocolVersion1
import com.secdec.bytefrog.agent.util.ControlSimulation
import com.secdec.bytefrog.agent.util.ErrorEnforcement
import com.codedx.codepulse.agent.common.config.RuntimeAgentConfigurationV1
import com.codedx.codepulse.agent.common.connect.Connection
import com.codedx.codepulse.agent.common.message.MessageProtocol
import com.codedx.codepulse.agent.common.message.MessageProtocolV1
import com.codedx.codepulse.agent.common.message.MessageConstantsV1

class ControlConnectionHandshakeV1Suite extends FunSuite with Matchers with ControlSimulation with ErrorEnforcement {
	val protocol = new ProtocolVersion1
	val messageProtocol = protocol.getMessageProtocol
	val handshake = protocol.getControlConnectionHandshake

	def connectionOf(in: DataInputStream, out: DataOutputStream) = new Connection {
		val input = in
		val output = out
		def close = {
			input.close
			output.close
		}
	}

	test("handshake should return configuration on success") {
		val config = new RuntimeAgentConfigurationV1(
			72, //run id
			7000, //heartbeat interval
			List[String]("java..*", "scala..*"), //exclusions
			List[String]("com.myproject.inc1"), //inclusions
			1024, //bufferMemoryBudget
			30, //queueRetryCount
			2 //numSenders
			)

		// serialize configuration
		val byteOutStream = new ByteArrayOutputStream
		val objectOut = new ObjectOutputStream(byteOutStream)
		objectOut.writeObject(config)
		objectOut.flush
		val configBytes = byteOutStream.toByteArray

		enforceNoErrors

		simulateControlCommunication { (in, out) =>
			// HQ reads a hello
			val message = in.readByte
			val version = in.readByte

			message should be(MessageConstantsV1.MsgHello)
			version should be(messageProtocol.protocolVersion)

			// write configuration
			messageProtocol.writeConfiguration(out, configBytes)
			out.flush
		} { (in, out) =>
			val result = handshake.performHandshake(connectionOf(in, out))

			// result should not be null and should match staged configuration
			result should not be (null)
			result.toString should be(config.toString)
		}
	}

	test("handshake should report error reply correctly") {
		val errorMessage = "error message"
		val expectedErrorMessage = s"received error from handshake: $errorMessage"

		enforceError(expectedErrorMessage)

		simulateControlCommunication { (in, out) =>
			// HQ reads a hello
			val message = in.readByte
			val version = in.readByte

			message should be(MessageConstantsV1.MsgHello)
			version should be(messageProtocol.protocolVersion)

			// and writes an error
			messageProtocol.writeError(out, errorMessage)
		} { (in, out) =>
			val result = handshake.performHandshake(connectionOf(in, out))

			result should be(null)
		}
	}

	test("handshake should report protocol errors correctly") {
		val expectedErrorMessage = "protocol error: invalid or unexpected control message"

		enforceError(expectedErrorMessage)

		simulateControlCommunication { (in, out) =>
			// HQ has gone bonkers, and just writes gibberish
			messageProtocol.writeDataHelloReply(out)
		} { (in, out) =>
			// agent should figure this out
			val result = handshake.performHandshake(connectionOf(in, out))

			result should be(null)
		}
	}
}
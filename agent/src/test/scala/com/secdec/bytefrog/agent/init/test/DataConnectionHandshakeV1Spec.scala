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

import java.io.DataInputStream
import java.io.DataOutputStream

import org.scalatest.FunSpec
import org.scalatest._
import org.scalatest.Matchers._

import com.codedx.codepulse.agent.protocol.ProtocolVersion1
import com.secdec.bytefrog.agent.util.ControlSimulation
import com.secdec.bytefrog.agent.util.ErrorEnforcement
import com.codedx.codepulse.agent.common.connect.Connection
import com.codedx.codepulse.agent.common.message.MessageConstantsV1

class DataConnectionHandshakeV1Spec extends FunSpec with Matchers with ControlSimulation with ErrorEnforcement {

	def connectionOf(in: DataInputStream, out: DataOutputStream) = new Connection {
		val input = in
		val output = out
		def close = {
			input.close
			output.close
		}
	}

	describe("DataConnectionHandshakeV1.performHandshake") {

		val protocol = new ProtocolVersion1
		val messageProtocol = protocol.getMessageProtocol
		val handshake = protocol.getDataConnectionHandshake

		it("should return true after a successful handshake") {
			simulateControlCommunication { (hqIn, hqOut) =>
				//read a DataHello message
				val msgByte = hqIn.readByte
				val runId = hqIn.readByte

				//reply
				messageProtocol.writeDataHelloReply(hqOut)

				//compare
				msgByte should equal(MessageConstantsV1.MsgDataHello)
				runId should equal(1)
			} { (agentIn, agentOut) =>
				//performHandshake is the method under test
				val result = handshake.performHandshake(1, connectionOf(agentIn, agentOut))
				result should equal(true)
			}
		}

		it("should return false in response to an invalid reply from HQ") {
			simulateControlCommunication { (hqIn, hqOut) =>
				//read a DataHello message
				val msgByte = hqIn.readByte
				val runId = hqIn.readByte

				//reply
				messageProtocol.writeHello(hqOut)

				//compare
				msgByte should equal(MessageConstantsV1.MsgDataHello)
				runId should equal(1)
			} { (agentIn, agentOut) =>
				//performHandshake is the method under test
				val result = handshake.performHandshake(1, connectionOf(agentIn, agentOut))
				result should equal(false)
			}
		}

		it("should return false and report the error on the event of an incoming error") {
			val errorMessage = "error"
			enforceError(s"received error during data handshake: $errorMessage")

			simulateControlCommunication { (hqIn, hqOut) =>
				//read a DataHello message
				val msgByte = hqIn.readByte
				val runId = hqIn.readByte

				//reply
				messageProtocol.writeError(hqOut, errorMessage)

				//compare
				msgByte should equal(MessageConstantsV1.MsgDataHello)
				runId should equal(1)
			} { (agentIn, agentOut) =>
				//performHandshake is the method under test
				val result = handshake.performHandshake(1, connectionOf(agentIn, agentOut))
				result should equal(false)
			}
		}
	}
}
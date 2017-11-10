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

package com.codedx.codepulse.hq.agent.test

import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import com.secdec.bytefrog.common.message.AgentOperationMode
import com.codedx.codepulse.hq.agent.AgentState
import com.codedx.codepulse.hq.agent.AgentStateCommand
import com.codedx.codepulse.hq.agent.AgentStateManager
import com.codedx.codepulse.hq.errors.TraceErrorController
import com.codedx.codepulse.hq.protocol.ControlMessage
import com.codedx.codepulse.hq.testutil.MockedSendingHelpers

class AgentStateManagerSpec extends FunSpec with MockFactory with ShouldMatchers with MockedSendingHelpers {

	private val tec = new TraceErrorController

	describe("agent state manager") {
		it("should start in initializing mode") {
			val asm = new AgentStateManager(mockControlConnection, tec)

			asm.current should be(AgentState.Initializing)
		}

		it("should keep track of the last state change") {
			val connection = mockControlConnection

			//val mp = mock[MessageProtocol]
			inSequence {
				(connection.writeMsg).expects(ControlMessage.Start).once
				(connection.writeMsg).expects(ControlMessage.Pause).once
			}

			val asm = new AgentStateManager(connection, tec)

			val lsc1 = asm.lastStateChange
			Thread.sleep(1000)
			asm.handleCommand(AgentStateCommand.Pause)
			val lsc2 = asm.lastStateChange

			lsc1 should be < (lsc2)
		}

		describe("initialization state") {
			it("should transition to tracing properly") {
				val connection = mockControlConnection

				connection.writeMsg.expects(ControlMessage.Start).once

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)

				asm.current should be(AgentState.Tracing)
			}

			it("should transition to suspended properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Suspend).once
					connection.writeMsg.expects(ControlMessage.Start).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Suspend)
				asm.handleCommand(AgentStateCommand.Start)

				asm.current should be(AgentState.Suspended)
			}

			describe("heartbeat checking") {
				def getStateManager: AgentStateManager = {
					val connection = mockControlConnection
					connection.writeMsg.expects(*).anyNumberOfTimes

					val asm = new AgentStateManager(connection, tec)
					asm.handleCommand(AgentStateCommand.Start)

					asm
				}

				it("should accept tracing heartbeat when appropriate") {
					val asm = getStateManager

					asm.handleCommand(AgentStateCommand.Suspend)
					asm.isHeartbeatModeExpected(AgentOperationMode.Tracing) should be(false)

					asm.handleCommand(AgentStateCommand.Resume)
					asm.isHeartbeatModeExpected(AgentOperationMode.Tracing) should be(true)
				}

				it("should not accept paused heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Paused) should be(false)
				}

				it("should accept suspended heartbeat when appropriate") {
					val asm = getStateManager

					asm.handleCommand(AgentStateCommand.Suspend)
					asm.isHeartbeatModeExpected(AgentOperationMode.Suspended) should be(true)

					asm.handleCommand(AgentStateCommand.Resume)
					asm.isHeartbeatModeExpected(AgentOperationMode.Suspended) should be(false)
				}

				it("should not accept shutdown heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Shutdown) should be(false)
				}
			}
		}

		describe("tracing state") {
			it("should throw if start command is sent again") {
				val connection = mockControlConnection

				connection.writeMsg.expects(ControlMessage.Start).once

				val asm = new AgentStateManager(connection, tec)
				asm.handleCommand(AgentStateCommand.Start)

				val thrown = intercept[IllegalStateException] {
					asm.handleCommand(AgentStateCommand.Start)
				}

				thrown.getMessage should be("unexpected command")
			}

			it("should transition to paused properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Pause).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Pause)

				asm.current should be(AgentState.Paused)
			}

			it("should transition to suspended properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Suspend).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Suspend)

				asm.current should be(AgentState.Suspended)
			}

			it("should transition to shutting-down properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Stop).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Stop)

				asm.current should be(AgentState.ShuttingDown)
			}

			it("should react to received shutdown properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Stop).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.ReceivedShutdown)

				asm.current should be(AgentState.ShuttingDown)
			}

			describe("heartbeat checking") {
				def getStateManager = {
					val connection = mockControlConnection
					connection.writeMsg.expects(ControlMessage.Start).once

					val asm = new AgentStateManager(connection, tec)
					asm.handleCommand(AgentStateCommand.Start)

					asm
				}

				it("should accept tracing heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Tracing) should be(true)
				}

				it("should not accept paused heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Paused) should be(false)
				}

				it("should not accept suspended heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Suspended) should be(false)
				}

				it("should not accept shutdown heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Shutdown) should be(false)
				}
			}
		}

		describe("paused state") {
			it("should throw if start command is sent again") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Pause).once
				}

				val asm = new AgentStateManager(connection, tec)
				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Pause)

				val thrown = intercept[IllegalStateException] {
					asm.handleCommand(AgentStateCommand.Start)
				}

				thrown.getMessage should be("unexpected command")
			}

			it("should transition to tracing properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Pause).once
					connection.writeMsg.expects(ControlMessage.Unpause).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Pause)
				asm.handleCommand(AgentStateCommand.Resume)

				asm.current should be(AgentState.Tracing)
			}

			it("should transition to suspended properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Pause).once
					connection.writeMsg.expects(ControlMessage.Unpause).once
					connection.writeMsg.expects(ControlMessage.Suspend).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Pause)
				asm.handleCommand(AgentStateCommand.Suspend)

				asm.current should be(AgentState.Suspended)
			}

			it("should transition to shutting-down properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Pause).once
					connection.writeMsg.expects(ControlMessage.Unpause).once
					connection.writeMsg.expects(ControlMessage.Stop).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Pause)
				asm.handleCommand(AgentStateCommand.Stop)

				asm.current should be(AgentState.ShuttingDown)
			}

			it("should react to received shutdown properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Pause).once
					connection.writeMsg.expects(ControlMessage.Unpause).once
					connection.writeMsg.expects(ControlMessage.Stop).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Pause)
				asm.handleCommand(AgentStateCommand.ReceivedShutdown)

				asm.current should be(AgentState.ShuttingDown)
			}

			describe("heartbeat checking") {
				def getStateManager = {
					val connection = mockControlConnection
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Pause).once

					val asm = new AgentStateManager(connection, tec)
					asm.handleCommand(AgentStateCommand.Start)
					asm.handleCommand(AgentStateCommand.Pause)

					asm
				}

				it("should not accept tracing heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Tracing) should be(false)
				}

				it("should accept paused heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Paused) should be(true)
				}

				it("should not accept suspended heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Suspended) should be(false)
				}

				it("should not accept shutdown heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Shutdown) should be(false)
				}
			}
		}

		describe("suspended state") {
			it("should throw if start command is sent again") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Suspend).once
				}

				val asm = new AgentStateManager(connection, tec)
				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Suspend)

				val thrown = intercept[IllegalStateException] {
					asm.handleCommand(AgentStateCommand.Start)
				}

				thrown.getMessage should be("unexpected command")
			}

			it("should transition to tracing properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Suspend).once
					connection.writeMsg.expects(ControlMessage.Unsuspend).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Suspend)
				asm.handleCommand(AgentStateCommand.Resume)

				asm.current should be(AgentState.Tracing)
			}

			it("should transition to paused properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Suspend).once
					connection.writeMsg.expects(ControlMessage.Unsuspend).once
					connection.writeMsg.expects(ControlMessage.Pause).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Suspend)
				asm.handleCommand(AgentStateCommand.Pause)

				asm.current should be(AgentState.Paused)
			}

			it("should transition to shutting-down properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Suspend).once
					connection.writeMsg.expects(ControlMessage.Unsuspend).once
					connection.writeMsg.expects(ControlMessage.Stop).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Suspend)
				asm.handleCommand(AgentStateCommand.Stop)

				asm.current should be(AgentState.ShuttingDown)
			}

			it("should react to received shutdown properly") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Suspend).once
					connection.writeMsg.expects(ControlMessage.Unsuspend).once
					connection.writeMsg.expects(ControlMessage.Stop).once
				}

				val asm = new AgentStateManager(connection, tec)

				asm.handleCommand(AgentStateCommand.Start)
				asm.handleCommand(AgentStateCommand.Suspend)
				asm.handleCommand(AgentStateCommand.ReceivedShutdown)

				asm.current should be(AgentState.ShuttingDown)
			}

			describe("heartbeat checking") {
				def getStateManager = {
					val connection = mockControlConnection
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Suspend).once

					val asm = new AgentStateManager(connection, tec)
					asm.handleCommand(AgentStateCommand.Start)
					asm.handleCommand(AgentStateCommand.Suspend)

					asm
				}

				it("should not accept tracing heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Tracing) should be(false)
				}

				it("should not accept paused heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Paused) should be(false)
				}

				it("should accept suspended heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Suspended) should be(true)
				}

				it("should not accept shutdown heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Shutdown) should be(false)
				}
			}
		}

		describe("shutting down state") {
			it("should ignore all transitions") {
				val connection = mockControlConnection

				inSequence {
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Stop).once
				}

				val asm = new AgentStateManager(connection, tec)
				asm.handleCommand(AgentStateCommand.Start)

				asm.handleCommand(AgentStateCommand.Stop)
				asm.current should be(AgentState.ShuttingDown)

				asm.handleCommand(AgentStateCommand.Start)
				asm.current should be(AgentState.ShuttingDown)

				asm.handleCommand(AgentStateCommand.Pause)
				asm.current should be(AgentState.ShuttingDown)

				asm.handleCommand(AgentStateCommand.Resume)
				asm.current should be(AgentState.ShuttingDown)

				asm.handleCommand(AgentStateCommand.Suspend)
				asm.current should be(AgentState.ShuttingDown)

				asm.handleCommand(AgentStateCommand.Resume)
				asm.current should be(AgentState.ShuttingDown)

				asm.handleCommand(AgentStateCommand.ReceivedShutdown)
				asm.current should be(AgentState.ShuttingDown)

				asm.handleCommand(AgentStateCommand.Stop)
				asm.current should be(AgentState.ShuttingDown)
			}

			describe("heartbeat checking") {
				def getStateManager = {
					val connection = mockControlConnection
					connection.writeMsg.expects(ControlMessage.Start).once
					connection.writeMsg.expects(ControlMessage.Stop).once

					val asm = new AgentStateManager(connection, tec)
					asm.handleCommand(AgentStateCommand.Start)
					asm.handleCommand(AgentStateCommand.Stop)

					asm
				}

				it("should not accept tracing heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Tracing) should be(false)
				}

				it("should not accept paused heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Paused) should be(false)
				}

				it("should not accept suspended heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Suspended) should be(false)
				}

				it("should accept shutdown heartbeat") {
					val asm = getStateManager

					asm.isHeartbeatModeExpected(AgentOperationMode.Shutdown) should be(true)
				}
			}
		}
	}
}
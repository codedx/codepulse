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

package com.secdec.bytefrog.agent.control.test

import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSpec
import org.scalatest.concurrent.Conductors

import com.secdec.bytefrog.agent.control.ControlMessageProcessorV1
import com.secdec.bytefrog.agent.control.ControlMessageHandler
import com.secdec.bytefrog.agent.control.ConfigurationReader
import com.secdec.bytefrog.agent.control.ConfigurationHandler
import com.secdec.bytefrog.agent.util.ErrorEnforcement
import com.secdec.bytefrog.agent.util.ControlSimulation
import com.secdec.bytefrog.common.config.RuntimeAgentConfigurationV1
import com.secdec.bytefrog.common.message.MessageProtocol
import com.secdec.bytefrog.common.message.MessageProtocolV1

class ControlMessageProcessorV1Suite extends FunSpec with ControlSimulation with ErrorEnforcement {
	val protocol: MessageProtocol = new MessageProtocolV1

	describe("control message processor") {
		it("should call onStart for start messages") {
			enforceNoErrors

			// expect onStart() one time
			val messageHandler = mock[ControlMessageHandler]
			(messageHandler.onStart _).expects.once

			val processor = new ControlMessageProcessorV1(mock[ConfigurationReader], messageHandler, mock[ConfigurationHandler])

			simulateHqWriteToAgent { stream =>
				// HQ sends start
				protocol.writeStart(stream)
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}

		it("should call onStop for stop messages") {
			enforceNoErrors

			// expect onStop() one time
			val messageHandler = mock[ControlMessageHandler]
			(messageHandler.onStop _).expects.once

			val processor = new ControlMessageProcessorV1(mock[ConfigurationReader], messageHandler, mock[ConfigurationHandler])

			simulateHqWriteToAgent { stream =>
				// HQ sends stop
				protocol.writeStop(stream)
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}

		it("should call onPause for pause messages") {
			enforceNoErrors

			// expect onPause() one time
			val messageHandler = mock[ControlMessageHandler]
			(messageHandler.onPause _).expects.once

			val processor = new ControlMessageProcessorV1(mock[ConfigurationReader], messageHandler, mock[ConfigurationHandler])

			simulateHqWriteToAgent { stream =>
				// HQ sends pause
				protocol.writePause(stream)
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}

		it("should call onUnpause for unpause messages") {
			enforceNoErrors

			// expect onUnpause() one time
			val messageHandler = mock[ControlMessageHandler]
			(messageHandler.onUnpause _).expects.once

			val processor = new ControlMessageProcessorV1(mock[ConfigurationReader], messageHandler, mock[ConfigurationHandler])

			simulateHqWriteToAgent { stream =>
				// HQ sends unpause
				protocol.writeUnpause(stream)
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}

		it("should call onSuspend for suspend messages") {
			enforceNoErrors

			// expect onSuspend() one time
			val messageHandler = mock[ControlMessageHandler]
			(messageHandler.onSuspend _).expects.once

			val processor = new ControlMessageProcessorV1(mock[ConfigurationReader], messageHandler, mock[ConfigurationHandler])

			simulateHqWriteToAgent { stream =>
				// HQ sends suspend
				protocol.writeSuspend(stream)
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}

		it("should call onUnsuspend for unsuspend messages") {
			enforceNoErrors

			// expect onUnsuspend() one time
			val messageHandler = mock[ControlMessageHandler]
			(messageHandler.onUnsuspend _).expects.once

			val processor = new ControlMessageProcessorV1(mock[ConfigurationReader], messageHandler, mock[ConfigurationHandler])

			simulateHqWriteToAgent { stream =>
				// HQ sends unsuspend
				protocol.writeUnsuspend(stream)
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}

		it("should call onConfig for configuration messages") {
			enforceNoErrors

			// expect onConfig() one time
			val configHandler = mock[ConfigurationHandler]
			(configHandler.onConfig _).expects(*).once

			val configReader = mock[ConfigurationReader]
			(configReader.readConfiguration _).expects(*).returns(new RuntimeAgentConfigurationV1(1, 1, null, null, 1, 1, 1)).anyNumberOfTimes

			val processor = new ControlMessageProcessorV1(configReader, mock[ControlMessageHandler], configHandler)

			simulateHqWriteToAgent { stream =>
				// send some dummy stuff
				protocol.writeConfiguration(stream, Array[Byte](1, 2, 3))
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}

		it("should call onError for error messages") {
			enforceNoErrors

			val errorMessage = "test error message"

			// expect onError(errorMessage) one time
			val messageHandler = mock[ControlMessageHandler]
			(messageHandler.onError _).expects(errorMessage).once

			val processor = new ControlMessageProcessorV1(mock[ConfigurationReader], messageHandler, mock[ConfigurationHandler])

			simulateHqWriteToAgent { stream =>
				// HQ sends error message
				protocol.writeError(stream, errorMessage)
			} { stream =>
				processor.processIncomingMessage(stream)
			}
		}
	}
}
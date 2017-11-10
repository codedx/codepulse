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

package com.secdec.bytefrog.agent.control.test

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalamock.scalatest.MockFactory

import com.secdec.bytefrog.agent.control.ModeChangeListener
import com.secdec.bytefrog.agent.control.StateManager
import com.secdec.bytefrog.agent.util.ErrorEnforcement
import com.secdec.bytefrog.agent.util.MockHelpers
import com.secdec.bytefrog.agent.util.StateManagerHelpers
import com.secdec.bytefrog.common.message.AgentOperationMode

class StateManagerSuite extends FunSpec with MockFactory with ShouldMatchers with ErrorEnforcement with MockHelpers with StateManagerHelpers {
	describe("state manager") {
		it("should start in initialization mode") {
			enforceNoErrors

			val sm = new StateManager

			sm.getCurrentMode should be(AgentOperationMode.Initializing)
		}

		it("should properly add mode change listeners") {
			val listener = mock[ModeChangeListener]
			(listener.onModeChange _).expects(*, *).once

			val sm = new StateManager

			sm.withListener(listener) {
				sm.triggerShutdown();
			}
		}

		it("should properly remove mode change listeners") {
			val listener = mock[ModeChangeListener]
			(listener.onModeChange _).expects(*, *).never

			val sm = new StateManager

			sm.withListener(listener) {
				sm.removeListener(listener)
				sm.triggerShutdown();
			}
		}

		it("should change to shutdown mode upon triggerShutdown()") {
			enforceNoErrors

			val sm = new StateManager
			sm.triggerShutdown()

			sm.getCurrentMode should be(AgentOperationMode.Shutdown)
		}

		describe("control message handler from getControlMessageHandler()") {
			it("should handle onStart properly") {
				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				val listener = mock[ModeChangeListener]
				(listener.onModeChange _).expects(AgentOperationMode.Initializing, AgentOperationMode.Tracing).once

				sm.withListener(listener) {
					cmh.onStart
				}

				sm.getCurrentMode should be(AgentOperationMode.Tracing)
			}

			it("should handle onStart properly (while suspended)") {
				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				val listener = mock[ModeChangeListener]
				(listener.onModeChange _).expects(AgentOperationMode.Initializing, AgentOperationMode.Suspended).once

				sm.withListener(listener) {
					cmh.onSuspend
					cmh.onStart
				}

				sm.getCurrentMode should be(AgentOperationMode.Suspended)
			}

			it("should handle onStop properly") {
				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				val listener = mock[ModeChangeListener]
				(listener.onModeChange _).expects(AgentOperationMode.Initializing, AgentOperationMode.Tracing).once
				(listener.onModeChange _).expects(AgentOperationMode.Tracing, AgentOperationMode.Shutdown).once

				sm.withListener(listener) {
					cmh.onStart
					cmh.onStop
				}

				sm.getCurrentMode should be(AgentOperationMode.Shutdown)
			}

			it("should handle onPause properly") {
				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				val listener = mock[ModeChangeListener]
				(listener.onModeChange _).expects(AgentOperationMode.Initializing, AgentOperationMode.Tracing).once
				(listener.onModeChange _).expects(AgentOperationMode.Tracing, AgentOperationMode.Paused).once

				sm.withListener(listener) {
					cmh.onStart
					cmh.onPause
				}

				sm.getCurrentMode should be(AgentOperationMode.Paused)

				// pause is only valid when tracing, so calling pause again should error
				enforceError("pause control message is only valid when tracing")
				cmh.onPause
			}

			it("should handle onUnpause properly") {
				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				val listener = mock[ModeChangeListener]
				(listener.onModeChange _).expects(AgentOperationMode.Initializing, AgentOperationMode.Tracing).once
				(listener.onModeChange _).expects(AgentOperationMode.Tracing, AgentOperationMode.Paused).once
				(listener.onModeChange _).expects(AgentOperationMode.Paused, AgentOperationMode.Tracing).once

				sm.withListener(listener) {
					cmh.onStart
					cmh.onPause
					cmh.onUnpause
				}

				sm.getCurrentMode should be(AgentOperationMode.Tracing)

				// unpause is only valid when paused, so calling unpause again should error
				enforceError("unpause control message is only valid when paused")
				cmh.onUnpause
			}

			it("should handle onSuspend properly") {
				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				val listener = mock[ModeChangeListener]
				(listener.onModeChange _).expects(AgentOperationMode.Initializing, AgentOperationMode.Tracing).once
				(listener.onModeChange _).expects(AgentOperationMode.Tracing, AgentOperationMode.Suspended).once

				sm.withListener(listener) {
					cmh.onStart
					cmh.onSuspend
				}

				sm.getCurrentMode should be(AgentOperationMode.Suspended)

				// suspend is only valid when tracing, so calling suspend again should error
				enforceError("suspend control message is only valid when tracing or initializing")
				cmh.onSuspend
			}

			it("should handle onUnsuspend properly") {
				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				val listener = mock[ModeChangeListener]
				(listener.onModeChange _).expects(AgentOperationMode.Initializing, AgentOperationMode.Tracing).once
				(listener.onModeChange _).expects(AgentOperationMode.Tracing, AgentOperationMode.Suspended).once
				(listener.onModeChange _).expects(AgentOperationMode.Suspended, AgentOperationMode.Tracing).once

				sm.withListener(listener) {
					cmh.onStart
					cmh.onSuspend
					cmh.onUnsuspend
				}

				sm.getCurrentMode should be(AgentOperationMode.Tracing)

				// unsuspend is only valid when suspended, so calling unsuspend again should error
				enforceError("unsuspend control message is only valid when suspended or initializing")
				cmh.onUnsuspend
			}

			it("should defer to ErrorHandler onError") {
				val errorMessage = "error message"
				val expectedError = s"received error from HQ: $errorMessage"

				enforceNoErrors

				val sm = new StateManager
				val cmh = sm.getControlMessageHandler

				enforceError(expectedError)
				cmh.onError(errorMessage)
			}
		}
	}
}
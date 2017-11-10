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

package com.secdec.bytefrog.agent.util

import scala.collection.mutable.ListBuffer

import org.scalatest.Suite
import org.scalatest.SuiteMixin

import com.secdec.bytefrog.agent.control.ModeChangeListener
import com.secdec.bytefrog.agent.control.StateManager

trait StateManagerHelpers extends SuiteMixin with Suite {
	implicit class PimpedStateManager(stateManager: StateManager) {
		def withListener(listener: ModeChangeListener)(body: => Unit) {
			try {
				stateManager.addListener(listener)
				body
			} finally {
				stateManager.removeListener(listener)
			}
		}
	}
}
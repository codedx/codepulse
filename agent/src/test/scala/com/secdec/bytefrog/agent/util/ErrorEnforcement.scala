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

import org.scalatest.TestSuite
import org.scalatest.TestSuiteMixin
import org.scalatest.Outcome
import org.scalamock.scalatest.MockFactory

import com.codedx.codepulse.agent.errors.ErrorListener
import com.codedx.codepulse.agent.errors.ErrorHandler

trait ErrorEnforcement extends TestSuiteMixin with TestSuite with MockFactory {
	private val mockedListeners = ListBuffer[ErrorListener]()

	private def registerMockedErrorListener(listener: ErrorListener): Unit = {
		ErrorHandler.addListener(listener)
		mockedListeners += listener
	}

	private def clearMockedListeners: Unit = {
		mockedListeners.foreach(ErrorHandler.removeListener _)
		mockedListeners.clear
	}

	def enforceNoErrors: Unit = {
		val listener = mock[ErrorListener]
		(listener.onErrorReported _).expects(*, *).never

		clearMockedListeners
		registerMockedErrorListener(listener)
	}

	def enforceError(errorMessage: String): Unit = {
		val listener = mock[ErrorListener]
		(listener.onErrorReported _).expects(errorMessage, *).once
		(listener.onErrorReported _).expects(*, *).anyNumberOfTimes

		clearMockedListeners
		registerMockedErrorListener(listener)
	}

	abstract override def withFixture(test: NoArgTest): Outcome = {
		try {
			super.withFixture(test)
		} finally {
			clearMockedListeners
		}
	}
}
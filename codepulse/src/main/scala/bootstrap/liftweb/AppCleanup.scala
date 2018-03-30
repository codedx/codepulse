/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
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

package bootstrap.liftweb

import com.codedx.codepulse.utility.Loggable

object AppCleanup extends Loggable {
	private var preShutdownHooks: List[() => Unit] = Nil

	private var shutdownHooks: List[() => Unit] = Nil

	def addPreShutdownHook(cleanup: () => Unit) = preShutdownHooks ::= cleanup

	def addShutdownHook(cleanup: () => Unit) = shutdownHooks ::= cleanup

	def runCleanup() = {
		try {
			logger.debug("Running PreShutdownHooks")
			preShutdownHooks.reverseIterator.foreach { _() }

			logger.debug("Running ShutdownHooks")
			shutdownHooks.reverseIterator.foreach { _() }
		} catch {
			case e: Throwable => e.printStackTrace
		}
	}
}
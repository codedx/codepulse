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

package com.codedx.codepulse.hq.util

/** Mixin for creating threads that run a semi-infinite loop. Implementors can choose to
  * override `preLoop` and `postLoop`. Implement `doLoop` to perform a single iteration
  * of the loop.
  */
trait LoopingThread extends Thread with Shutdown {

	protected def preLoop: Unit = ()
	protected def doLoop: Unit
	protected def postLoop: Unit = ()

	private var _shuttingDown = false
	def shuttingDown = _shuttingDown
	def shutdown = {
		if (!_shuttingDown) {
			_shuttingDown = true
			interrupt
		}
	}

	override def run = {
		preLoop
		while (!shuttingDown) {
			try {
				doLoop
			} catch {
				case e: InterruptedException =>
				//broke out of doLoop due to interrupt, but
				//don't shut down unless we were told to
			}
		}
		postLoop
	}

}
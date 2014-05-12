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

package com.secdec.codepulse.dependencycheck

import java.io.File
import akka.actor._

object DependencyCheckActor {
	case object Update
	case class Run(scanSettings: ScanSettings)(callback: File => Unit) {
		private[dependencycheck] def apply(file: File) = callback(file)
	}
}

/** Actor to facilitate dependency check tasks.
  *
  * @author robertf
  */
class DependencyCheckActor extends Actor with Stash {

	import DependencyCheckActor._
	import Settings.defaultSettings

	def receive = {
		case Update => update
		case cb: Run => run(cb)
	}

	private def update() {
		DependencyCheck.doUpdates
	}

	private def run(cb: Run) {
		val result = DependencyCheck.runScan(cb.scanSettings)
		cb(result)
	}
}
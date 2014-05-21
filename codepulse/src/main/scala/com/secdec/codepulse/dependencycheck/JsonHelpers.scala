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

import com.secdec.codepulse.data.model._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._

object JsonHelpers {
	implicit class JsonDependencyCheckStatus(status: DependencyCheckStatus) {
		def json: JObject = status match {
			case DependencyCheckStatus.Queued => ("state" -> "queued")
			case DependencyCheckStatus.Running => ("state" -> "running")
			case DependencyCheckStatus.Finished(numDeps, numFlagged) => ("state" -> "finished") ~
				("numDeps" -> numDeps) ~ ("numFlaggedDeps" -> numFlagged)
			case DependencyCheckStatus.Failed => ("state" -> "failed")
			case DependencyCheckStatus.NotRun => ("state" -> "none")
			case DependencyCheckStatus.Unknown => ("state" -> "unknown")
		}
	}
}
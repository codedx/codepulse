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

package com.secdec.codepulse.components.dependencycheck

import net.liftweb.http.CometActor
import net.liftweb.http.js.JE._
import net.liftweb.http.js._
import JsCmds.jsExpToJsCmd
import net.liftweb.http.js.jquery.JqJE._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._

import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.dependencycheck._
import com.secdec.codepulse.util.comet.PublicCometInit
import JsonHelpers._

object Updates extends CometActor with PublicCometInit {
	def pushUpdate(projectId: ProjectId, status: DependencyCheckStatus, vulnerableNodes: Seq[Int]) {
		val update = ("project" -> projectId.num) ~
			("summary" -> status.json) ~ ("vulnerableNodes" -> vulnerableNodes)
		val cmd: JsCmd = Jq(JsVar("document")) ~> JsFunc("trigger", "dependencycheck-update", update)
		partialUpdate { cmd }
	}

	def render = Nil
}
/*
 * Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */

package com.secdec.codepulse.tracer.snippet

import com.secdec.codepulse.tracer.ProjectManager
import com.secdec.codepulse.util.comet.PublicCometInit
import net.liftweb.http.js.JE.JsFunc
import net.liftweb.http.js.JE.JsVar
import net.liftweb.http.js.JsCmds.jsExpToJsCmd
import net.liftweb.http.js.JsExp.strToJsExp
import net.liftweb.http.js.jquery.JqJE.Jq
import reactive.ForeachableForwardable
import reactive.Observing

/** A super simple comet actor that calls `$(document).trigger('projectUpdated')`
  * whenever the given `projectManager` fires an event on its `projectListUpdates` stream.
  */
class ProjectUpdated(projectManager: ProjectManager) extends PublicCometInit with Observing {
	// no visible components
	def render = Nil

	override def localSetup() = {
		super.localSetup()

		projectManager.projectListUpdates ->> partialUpdate {
			Jq(JsVar("document")) ~> JsFunc("trigger", "projectUpdated")
		}
	}
}
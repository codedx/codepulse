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

package com.secdec.codepulse.pages.traces

import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.tracer.ProjectManager
import com.secdec.codepulse.tracer.TracingTarget
import net.liftweb.common.Box
import net.liftweb.common.Box._
import net.liftweb.sitemap.Menu

object ProjectDetailsPage {

	def projectMenu(projectManager: ProjectManager) = {
		def parse(link: String): Box[TracingTarget] = for {
			projectId <- ProjectId unapply link
			target <- (projectManager getProject projectId) ?~ "Project doesn't exist"
		} yield target
		def encode(target: TracingTarget): String = target.id.num.toString

		Menu.param[TracingTarget]("Project", "Project", parse, encode) / "projects"
	}

	def projectHref(projectId: ProjectId) = s"/projects/${projectId.num}"

}

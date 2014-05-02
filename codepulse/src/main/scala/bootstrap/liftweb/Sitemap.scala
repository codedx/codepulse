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

import net.liftweb.http.RedirectResponse
import net.liftweb.sitemap.ConvertableToMenu
import net.liftweb.sitemap.Menu
import net.liftweb.sitemap.Loc._
import net.liftweb.sitemap.**
import net.liftweb.sitemap.SiteMap

import com.secdec.codepulse.pages.traces.ProjectDetailsPage
import com.secdec.codepulse.tracer.ProjectManager

/** Provides the sitemap for lift.
  */
object Sitemap {

	/** Builds a SiteMap based on all of the current values of the `var`s in this object.
	  */
	def buildSitemap(projectManager: ProjectManager): SiteMap = SiteMap(homeMenu, projectDetailsMenu(projectManager))

	val homeMenu = Menu.i("Home") / "index"

	def projectDetailsMenu(projectManager: ProjectManager) = ProjectDetailsPage.projectMenu(projectManager) >> Hidden
}
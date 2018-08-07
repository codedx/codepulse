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

import scala.language.implicitConversions
import scala.language.postfixOps

import com.secdec.codepulse.components.includes.snippet.Includes
import net.liftweb.common.Empty
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.http._
import net.liftweb.http.LiftRulesMocker.toLiftRules
import net.liftweb.util.Helpers.intToTimeSpanBuilder
import net.liftweb.util.Vendor.valToVender
import net.liftweb.http.RedirectWithState
import com.secdec.codepulse.{CodePulseLogging, dependencycheck, tracer}

/** A class that's instantiated early and run.  It allows the application
  * to modify lift's environment
  */
class Boot extends Loggable {
	def boot {

		CodePulseLogging.init

		//do not log timing info for every request that Lift serves.
		LiftRules.logServiceRequestTiming = false

		LiftRules.ajaxPostTimeout = 30000

		//see FogBugz case 638
		LiftRules.cometRequestTimeout = Full(25)

		ExceptionHandler.init(LiftRules)

		tracer.boot
		dependencycheck boot(tracer.actorSystem(), tracer.generalEventBus())

		/* Register all Comet actors and Snippet renderers, for use by
		 * Lift's templating system. Without this, all of our snippets
		 * would break.
		 * 
		 * Note: Lift's usual style for enabling snippets is to use
		 * `addToPackages`, so that it can find classes by reflection.
		 * Due to our use of obfuscation, that is undesireable, so
		 * every snippet must be explicitly registered in BootSnippets.
		 */
		BootSnippets(tracer.projectManager)

		// initialize the "Includes"
		Includes.init

		// Initialize the event turbine, 
		// then make sure it gets shut down when the Lift servlet gets destroyed.
		LiftRules.unloadHooks.append { () =>
			AppCleanup.runCleanup()
		}

		// Set Lift's SiteMap based on the SitemapConfig object.
		LiftRules.setSiteMap(Sitemap.buildSitemap(tracer.projectManager))

		LiftRules.uriNotFound.prepend {
//			case (req, _) => new NotFoundAsResponse(RedirectWithState("/", RedirectState(() => NotFoundResponse("Attempted to redirect to a page that no longer exists"))))
			case (req, _) => new NotFoundAsResponse(RedirectResponse("/", req))
		}

		LiftRules.jsArtifacts = net.liftweb.http.js.jquery.JQueryArtifacts

		// Force the request to be UTF-8
		LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

		// Use HTML5 for rendering
		LiftRules.htmlProperties.default.set((r: Req) =>
			new Html5Properties(r.userAgent))

		// Auto-fadeout Notices
		LiftRules.noticesAutoFadeOut.default.set((notices: NoticeType.Value) => {
			notices match {
				case NoticeType.Notice => Full((4 seconds, 2 seconds))
				case _ => Empty
			}
		})

		FileUploadSetup.init(LiftRules)
	}
}

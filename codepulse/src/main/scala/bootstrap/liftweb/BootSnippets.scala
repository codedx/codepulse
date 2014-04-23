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

import com.secdec.codepulse.components.includes.snippet.Includes
import com.secdec.codepulse.components.notifications.Notifications
import com.secdec.codepulse.components.version.snippet.VersionSnippet
import com.secdec.codepulse.tracer.TraceManager
import com.secdec.codepulse.tracer.TracingTarget
import com.secdec.codepulse.tracer.snippet.CometTracerUI
import com.secdec.codepulse.tracer.snippet.TraceListUpdates
import com.secdec.codepulse.tracer.snippet.TraceWidgetry
import com.secdec.codepulse.util.comet.PublicCometInit

import net.liftweb.common.Full
import net.liftweb.http.CometCreationInfo
import net.liftweb.http.LiftRules
import net.liftweb.http.LiftRulesMocker.toLiftRules
import net.liftweb.util.StringHelpers

private[liftweb] object BootSnippets {
	def apply(traceManager: TraceManager) = {
		LiftRules.snippetDispatch.prepend {
			case SnippetRequest("Includes", _) => Includes
			case SnippetRequest("VersionSnippet", _) => new VersionSnippet
			case SnippetRequest("TraceWidgetry", Full(target: TracingTarget)) => new TraceWidgetry(traceManager, target)
			case SnippetRequest("Notifications", _) => Notifications
		}

		val cometActorsByName: PartialFunction[String, PublicCometInit] = {
			case CometTracerUI.className => new CometTracerUI
			case "TraceListUpdates" => new TraceListUpdates(traceManager)
			case "Notifications" => Notifications
		}

		LiftRules.cometCreation append {
			case CometCreationInfo(cType, name, xml, attribs, session) if cometActorsByName.isDefinedAt(StringHelpers.camelify(cType)) =>
				val comet = cometActorsByName(StringHelpers.camelify(cType))
				comet.initCometActor(session, Full(cType), name, xml, attribs)
				comet
		}
	}
}
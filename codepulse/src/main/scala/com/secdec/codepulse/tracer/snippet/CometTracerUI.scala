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

package com.secdec.codepulse.tracer.snippet

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.NodeSeq

import com.secdec.codepulse.tracer.TracingTarget
import com.secdec.codepulse.tracer.TracingTargetState
import com.secdec.codepulse.util.comet.CometWidget
import com.secdec.codepulse.util.comet.CometWidgetCompanion
import net.liftweb.common.Loggable
import net.liftweb.http.js.JE.JsFunc
import net.liftweb.http.js.JE.JsVar
import net.liftweb.http.js.JsCmds.jsExpToJsCmd
import net.liftweb.http.js.JsExp.strToJsExp
import net.liftweb.http.js.jquery.JqJE.Jq
import net.liftweb.json.{ DefaultFormats, Serialization, ShortTypeHints }
import net.liftweb.json.Serialization.write
import reactive.Observing
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._

object CometTracerUI extends CometWidgetCompanion[TracingTarget, CometTracerUI] {
	val className = "CometTracerUi"
}

class CometTracerUI extends CometWidget[TracingTarget, CometTracerUI]
	with Loggable with Observing {
	def companion = CometTracerUI

	private def tracingTarget = data.getOrElse { throw new IllegalStateException("Access data before setup") }

	override def localSetup() = {
		super.localSetup()

		tracingTarget.subscribeToStateChanges { stateChanges =>
			stateChanges foreach { sendStateUpdate }
		}
	}

	def sendStateUpdate(state: TracingTargetState) = partialUpdate {
		implicit val formats = DefaultFormats
		val status: JObject = ("name" -> state.name) ~ ("information" -> state.information)
		Jq(JsVar("document")) ~> JsFunc("trigger", "tracer-state-change", status)
	}

	def render = NodeSeq.Empty
}
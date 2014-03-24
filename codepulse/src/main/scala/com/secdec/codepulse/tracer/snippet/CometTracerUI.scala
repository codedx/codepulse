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

import scala.xml.NodeSeq

import com.secdec.codepulse.util.comet.CometWidget
import com.secdec.codepulse.util.comet.CometWidgetCompanion
import com.secdec.codepulse.tracer.TracingTarget
import com.secdec.codepulse.tracer.TracingTargetEvent
import com.secdec.bytefrog.hq.trace.TraceEndReason

import net.liftweb.common.Loggable
import net.liftweb.http.js.JE.JsFunc
import net.liftweb.http.js.JE.JsVar
import net.liftweb.http.js.JsCmds.jsExpToJsCmd
import net.liftweb.http.js.JsExp.jValueToJsExp
import net.liftweb.http.js.JsExp.strToJsExp
import net.liftweb.http.js.jquery.JqJE.Jq
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL.map2jvalue
import net.liftweb.json.JsonDSL.string2jvalue
import reactive.Observing

object CometTracerUI extends CometWidgetCompanion[TracingTarget, CometTracerUI] {
	val className = "CometTracerUi"
}

class CometTracerUI extends CometWidget[TracingTarget, CometTracerUI]
	with Loggable with Observing {
	def companion = CometTracerUI

	private def tracingTarget = data.getOrElse { throw new IllegalStateException("Access data before setup") }

	override def localSetup() = {
		super.localSetup()

		tracingTarget.subscribe { events =>
			events foreach {
				case TracingTargetEvent.Connecting =>
					sendStatusUpdate { Map("state" -> "connecting") }
				case TracingTargetEvent.Connected =>
					sendStatusUpdate { Map("state" -> "connected") }
				case TracingTargetEvent.Started(targetId) =>
					sendStatusUpdate { Map("state" -> "started") }
				case TracingTargetEvent.Finished(reason) =>
					val s = reason match {
						case TraceEndReason.Normal => "normal"
						case TraceEndReason.Halted => "halted"
					}
					sendStatusUpdate { Map("state" -> "finished", "reason" -> s) }
			}
		}
	}

	def sendStatusUpdate(data: JValue) = partialUpdate {
		Jq(JsVar("document")) ~> JsFunc("trigger", "tracer-state-change", data)
	}

	def render = NodeSeq.Empty
}
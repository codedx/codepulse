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

import com.secdec.codepulse.tracer.TraceConnectionLooper._
import com.secdec.codepulse.tracer.traceConnectionLooper
import com.secdec.codepulse.util.comet.JqEventSupport
import com.secdec.codepulse.util.comet.PublicCometInit

import net.liftweb.http.CometActor
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import reactive.Observing

class TraceConnectorStateChanges
	extends CometActor with PublicCometInit with JqEventSupport with Observing {

	def render = Nil

	override def localSetup() = {
		super.localSetup()

		traceConnectionLooper.watchStateChanges { changes =>
			changes foreach { state =>
				jqTriggerGlobal("connector-state-change" -> stateAsJson(state))
			}
		}
	}

	def stateAsJson(state: State): JValue = state match {
		case Idle => "state" -> "idle"
		case Connecting => "state" -> "connecting"
		case Running(target) =>
			("state" -> "running") ~ ("tracedProject" -> target.id.num)
	}
}

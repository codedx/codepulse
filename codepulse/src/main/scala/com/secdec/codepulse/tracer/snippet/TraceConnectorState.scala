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

import net.liftweb.http.DispatchSnippet
import scala.xml.NodeSeq
import net.liftweb.util.BindHelpers
import scala.xml.Elem
import com.secdec.codepulse.tracer.TraceConnectionLooper
import com.secdec.codepulse.tracer.traceConnectionLooper
import scala.concurrent.Await
import scala.concurrent.duration._

/** A snippet that adds a css class to the templated element to indicate
  * the current state of the `traceConnectionLooper`.
  *
  * The possible states are represented as `trace-idle`, `trace-connecting`,
  * or `trace-running`.
  */
object TraceConnectorState extends DispatchSnippet {

	def dispatch = {
		case "render" => doRender
	}

	/** Adds the "trace-xxx" css class to the templated element,
	  * where "xxx" is replaced with "idle", "connecting", or
	  * "running" depending on the state of the `traceConnectionLooper`.
	  *
	  * If the looper is currently "running", this also adds the
	  * `data-traced-project="<project id>"` attribute to the
	  * templated element.
	  */
	def doRender(template: NodeSeq): NodeSeq = {
		val state = getConnectorState
		val stateClass = "trace-" + stateName(state)
		val stateMeta = stateMetadata(state)
		BindHelpers.addAttributes(template, stateMeta) map {
			case e: Elem =>
				BindHelpers.addCssClass(stateClass, e)
			case n => n
		}
	}

	def getConnectorState: TraceConnectionLooper.State = {
		// the looper's state is received via message passing,
		// so it returned as a future. We just need to wait
		// a tiny bit.
		val stateFuture = traceConnectionLooper().getState

		Await.result(stateFuture, 1.second)
	}

	def stateName(state: TraceConnectionLooper.State) = state match {
		case TraceConnectionLooper.Idle => "idle"
		case TraceConnectionLooper.Connecting => "connecting"
		case TraceConnectionLooper.Running(_) => "running"
	}

	def stateMetadata(state: TraceConnectionLooper.State): xml.MetaData = state match {
		case TraceConnectionLooper.Running(target) =>
			new xml.UnprefixedAttribute("data-traced-project", target.id.num.toString, xml.Null)
		case _ =>
			xml.Null
	}
}
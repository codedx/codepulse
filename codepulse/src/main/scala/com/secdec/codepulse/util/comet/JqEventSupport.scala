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

package com.secdec.codepulse.util.comet

import scala.language.implicitConversions

import net.liftweb.http.CometActor
import net.liftweb.http.js.JE.JsFunc
import net.liftweb.http.js.JE.JsFunc
import net.liftweb.http.js.JE.JsVar
import net.liftweb.http.js.JE.JsVar
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds
import net.liftweb.http.js.JsCmds.jsExpToJsCmd
import net.liftweb.http.js.JsExp
import net.liftweb.http.js.JsExp.strToJsExp
import net.liftweb.http.js.jquery.JqJE.Jq
import net.liftweb.http.js.jquery.JqJE.Jq
import net.liftweb.json.JsonAST.JValue

/** Utility mixin that makes it a bit easier (less verbose) to trigger arbitrary
  * jQuery events on the front end via comet.
  *
  * Calling `jqTriggerGlobal` allows client code to cause the following javascript
  * to be called in the browser:
  *
  * `$(document).trigger(eventName, args...)`
  *
  * The `EventParams` trait and companion object allow for several different
  * ways to specify the eventName and arguments.
  *
  * `jqTriggerGlobal("myEvent")` maps to `$(document).trigger('myEvent')`.
  * `jqTriggerGlobal("myEvent" -> args) maps to `$(document).trigger('myEvent', args)`,
  * where `args` was a JSON object, or a sequence of `JsExp` values.
  */
trait JqEventSupport { self: CometActor =>

	protected def jqTriggerGlobalCmd(event: String, args: JsExp*): JsCmd = {
		val triggerArgs = Seq[JsExp](event) ++ args
		Jq(JsVar("document")) ~> JsFunc("trigger", triggerArgs: _*)
	}

	protected def jqTriggerGlobal(events: EventParams*) = partialUpdate {
		val cmds = events map { params =>
			val (event, args) = params.toRaw
			jqTriggerGlobalCmd(event, args: _*)
		}
		cmds.foldLeft(JsCmds.Noop) { _ & _ }
	}

	sealed trait EventParams {
		def toRaw: (String, Seq[JsExp])
	}
	object EventParams {
		case class NoArgs(event: String) extends EventParams {
			def toRaw = (event, Nil)
		}
		case class JsonArgs(event: String, arg: JValue) extends EventParams {
			def toRaw = (event, Seq[JsExp](arg))
		}
		case class RawArgs(event: String, args: Seq[JsExp]) extends EventParams {
			def toRaw = (event, args)
		}

		implicit def string2EventParams(event: String) = NoArgs(event)
		implicit def stringJValue2EventParams(sj: (String, JValue)) = JsonArgs(sj._1, sj._2)
		implicit def rawParams2EventParams(raw: (String, Seq[JsExp])) = RawArgs(raw._1, raw._2)
	}
}
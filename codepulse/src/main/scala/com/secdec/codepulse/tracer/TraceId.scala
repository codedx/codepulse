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

package com.secdec.codepulse.tracer
import net.liftweb.util.Helpers.AsInt

/** Identifier class associated with TracingTargets.
  *
  * A TraceId is actually a simple wrapper class containing
  * an integer. TraceIds are used as keys for looking up
  * traces from a TraceManager, as well as being key segments
  * of some of the REST APIs associated with traces.
  */
case class TraceId(num: Int) extends Ordered[TraceId] {
	def compare(that: TraceId) = this.num - that.num
	def +(n: Int) = TraceId(num + n)
}

object TraceId {
	/** Extract a TraceId from a String if that string is a string of digits */
	def unapply(s: String) = s match {
		case AsInt(num) => Some(TraceId(num))
		case _ => None
	}
}
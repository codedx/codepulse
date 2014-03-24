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

import com.secdec.codepulse.tracer.TraceId
import com.secdec.codepulse.tracer.TraceManager
import com.secdec.codepulse.tracer.TracingTarget

import net.liftweb.common.Box
import net.liftweb.sitemap.Menu

object TraceDetailsPage {

	def traceMenu(traceManager: TraceManager) = {
		def parse(link: String): Box[TracingTarget] = for {
			traceId <- TraceId unapply link
			target <- traceManager getTrace traceId
		} yield target
		def encode(target: TracingTarget): String = target.id.num.toString

		Menu.param[TracingTarget]("Trace", "Trace", parse, encode) / "traces"
	}

	def traceHref(traceId: TraceId) = s"/traces/${traceId.num}"

}

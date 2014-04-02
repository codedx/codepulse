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
import net.liftweb.http.DispatchSnippet
import com.secdec.codepulse.tracer.TracingTarget
import scala.xml.Text
import net.liftweb.util.BindHelpers._
import java.text.SimpleDateFormat
import java.util.Date
import com.secdec.codepulse.tracer
import com.secdec.codepulse.tracer.TraceManager

class TraceWidgetry(manager: TraceManager, target: TracingTarget) extends DispatchSnippet {

	def dispatch = {
		case "render" => renderTraceWidgetry
		case "name" => renderName
	}

	def renderTraceWidgetry(template: NodeSeq): NodeSeq = {

		val (cometName, cometTemplate) = CometTracerUI.create(target)
		val dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy 'at' HH:mm:ss z")
		val data = target.traceData

		val creationDate = dateFormat.format(new Date(data.metadata.creationDate))
		val importDate = data.metadata.importDate map { d =>
			dateFormat.format(new Date(d))
		}

		def runBinding(xml: NodeSeq): NodeSeq = bind("trace", xml,
			"name" -> Text(data.metadata.name),
			"creationdate" -> Text(creationDate),
			"importdate" -> Text(importDate getOrElse "???"),
			"ifimported" -> { (xml: NodeSeq) =>
				// note the recursion, since we potentially nest other bindings inside <ifimported>
				if (importDate.isDefined) runBinding(xml) else NodeSeq.Empty
			},
			"nameconflict" -> { (xml: NodeSeq) =>
				//detect a name conflict
				val thisId = target.id
				val conflictClass = manager.tracesIterator
					.filterNot(_.id == thisId) // only check other traces
					.find(_.traceData.metadata.name == target.traceData.metadata.name) // find one with the same name as this
					.map { _ => "hasConflict" } // if found, this will be a Some

				// If there was a conflict, this div has the "nameConflict" class attribute.
				// Otherwise, the class attribute will be blank.
				val result = <div class="nameConflict">{ runBinding(xml) }</div>

				addCssClass(conflictClass, result)
			},
			"exportlink" -> { (xml: NodeSeq) =>
				val href = tracer.traceAPIServer.Paths.Export.toHref(target)
				<a data-downloader={ href } data-filename={ s"${data.metadata.name}.pulse" }>{ runBinding(xml) }</a>
			})

		cometTemplate +: runBinding(template)
	}

	def renderName(ignored: NodeSeq): NodeSeq = {
		Text(target.traceData.metadata.name)
	}
}
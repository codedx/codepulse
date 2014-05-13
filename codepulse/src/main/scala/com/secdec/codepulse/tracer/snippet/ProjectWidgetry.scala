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

import java.io.File
import java.util.Locale

import scala.xml.NodeSeq
import scala.xml.Text

import org.joda.time.format.DateTimeFormat

import com.secdec.codepulse.tracer.ProjectManager
import com.secdec.codepulse.tracer.TracingTarget
import com.secdec.codepulse.tracer.TracingTargetState
import com.secdec.codepulse.tracer.apiServer

import net.liftweb.common.Box.option2Box
import net.liftweb.http.DispatchSnippet
import net.liftweb.util.BindHelpers.addCssClass
import net.liftweb.util.BindHelpers.bind
import net.liftweb.util.BindHelpers.strToSuperArrowAssoc

class ProjectWidgetry(manager: ProjectManager, target: TracingTarget) extends DispatchSnippet {

	def dispatch = {
		case "render" => renderProjectWidgetry
		case "name" => renderName
	}

	def renderProjectWidgetry(template: NodeSeq): NodeSeq = {

		val localDateFormatPattern = DateTimeFormat.patternForStyle("SS", Locale.getDefault)
		val dateFormat = DateTimeFormat.forPattern(localDateFormatPattern)
		val data = target.projectData

		val creationDate = dateFormat.print(data.metadata.creationDate)
		val importDate = data.metadata.importDate map { d =>
			dateFormat.print(d)
		}

		def runBinding(xml: NodeSeq): NodeSeq = bind("project", xml,
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

				val conflictClass = manager.projectsIterator
					.filterNot(_.id == thisId) // only check other projects
					.filter { // ignore projects that are deleted or are being deleted
						_.getStateSync match {
							case Some(TracingTargetState.DeletePending | TracingTargetState.Deleted) => false
							case _ => true
						}
					}
					.find(_.projectData.metadata.name == target.projectData.metadata.name) // find one with the same name as this
					.map { _ => "hasConflict" } // if found, this will be a Some

				val untitledClass =
					if (target.projectData.metadata.hasCustomName) None
					else Some("noProjectName")

				val result = <div class="nameConflict">{ runBinding(xml) }</div>

				// If the project was untitled, the result will add the 'noProjectName' class;
				// Otherwise, and if there was a conflict, the result will add the 'hasConflict' class;
				// If neither of these are true, the result is unmodified.
				addCssClass(untitledClass orElse conflictClass, result)
			},
			"exportlink" -> { (xml: NodeSeq) =>
				val href = apiServer.Paths.Export.toHref(target)
				<a data-downloader={ href } data-filename={ s"${data.metadata.name}.pulse" }>{ runBinding(xml) }</a>
			},
			"stateupdates" -> { (xml: NodeSeq) =>
				val (cometName, cometTemplate) = CometTracerUI.create(target)
				cometTemplate
			})

		runBinding(template)
	}

	def renderName(ignored: NodeSeq): NodeSeq = {
		Text(target.projectData.metadata.name)
	}
}
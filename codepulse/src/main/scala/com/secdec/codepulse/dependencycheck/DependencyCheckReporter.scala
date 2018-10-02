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

package com.secdec.codepulse.dependencycheck

import scala.xml._
import java.io.File

import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._

import com.secdec.codepulse.data.model.{ ProjectData, TreeNodeData, TreeNodeFlag }
import com.secdec.codepulse.paths
import com.secdec.codepulse.util.Implicits._

/** Helper for generating report data for dependency check reports.
  *
  * @author robertf
  */
object DependencyCheckReporter {
	private val CweIdR = raw"^CWE-(\d+) ".r.unanchored

	private def cveUrl(cveName: String) = s"http://web.nvd.nist.gov/view/vuln/detail?vulnId=$cveName"
	private def cweUrl(cweId: Int) = s"http://cwevis.org/browse/$cweId"

	def buildReport(project: ProjectData, interestedNodes: Seq[Int]): JValue = {
		val treeNodeData = project.treeNodeData
		import treeNodeData.ExtendedTreeNodeData

		val reportFolder = paths.appData / "dependency-check" / "projects" / project.id.num.toString
		val xmlReportFile = reportFolder / "dependency-check-report.xml"
		val htmlReportFile = reportFolder / "dependency-check-report.html"
		if (!xmlReportFile.exists || !htmlReportFile.exists) ???

		val reportNodes = interestedNodes.flatMap(treeNodeData.getNode(_))

		val xml = XML loadFile xmlReportFile
		val name = (xml \ "projectInfo" \ "name").text
		val base = new File(((xml \\ "dependency").head \ "filePath").text)
		val baseSegmentCount = base.pathSegments.length

		val interested = reportNodes.map(_.label).toSet

		val vulns = for {
			dep <- xml \\ "dependency"
			file = new File((dep \ "filePath").text)
			jarLabel = file.pathSegments.drop(baseSegmentCount).mkString("/")
			jarPath = file.pathSegments.drop(baseSegmentCount).mkString("JARs/", "/", "")
			if interested contains jarPath
			vulns = dep \\ "vulnerability"
		} yield {
			("jar" -> jarLabel) ~
			("cves" -> vulns.map { vuln =>
				val name = (vuln \ "name").text

				val cweName = (vuln \ "cwe").text
				val cwe: JValue = cweName match {
					case CweIdR(cweId) => ("name" -> cweName) ~ ("url" -> cweUrl(cweId.toInt))
					case _ => JNull
				}

				("name" ->  name) ~
				("cwe" -> cwe) ~
				("url" -> cveUrl(name)) ~
				("description" -> (vuln \ "description").text)
			})
		}

		("name" -> name) ~ ("report" -> htmlReportFile.toURI.toString) ~ ("vulns" -> vulns)
	}
}
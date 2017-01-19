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

import java.io.File

import org.apache.commons.io.IOUtils
import org.owasp.dependencycheck.reporting.ReportGenerator.{ Format => DCReportFormat }
import org.owasp.dependencycheck.utils.{ Settings => DepCheckSettings }

import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.paths
import com.secdec.codepulse.util.RichFile._

sealed abstract class ReportFormat(val value: DCReportFormat)
object ReportFormat {
	case object Xml extends ReportFormat(DCReportFormat.XML)
	case object Html extends ReportFormat(DCReportFormat.HTML)
	case object Vuln extends ReportFormat(DCReportFormat.VULN)
	case object All extends ReportFormat(DCReportFormat.ALL)
}

sealed trait ApplicableSettings {
	def applySettings(): Unit
}

case class Settings(
	dataDir: File
) extends ApplicableSettings {

	def applySettings() {
		DepCheckSettings.setString(DepCheckSettings.KEYS.DATA_DIRECTORY, dataDir.getAbsolutePath)
		DepCheckSettings.setBoolean(DepCheckSettings.KEYS.ANALYZER_JAR_ENABLED, true)
		DepCheckSettings.setBoolean(DepCheckSettings.KEYS.ANALYZER_ARCHIVE_ENABLED, true)
		DepCheckSettings.setBoolean(DepCheckSettings.KEYS.ANALYZER_NEXUS_ENABLED, true)
		DepCheckSettings.setBoolean(DepCheckSettings.KEYS.ANALYZER_NEXUS_USES_PROXY, false)
	}
}

object Settings {
	lazy val defaultDataDir = paths.localAppData / "dependency-check" / "data"

	implicit val defaultSettings = Settings(dataDir = defaultDataDir)
}

case class ScanSettings(
	app: File, appName: String,
	reportDir: File, reportFormat: ReportFormat
)

object ScanSettings {
	def apply(app: File, appName: String, projectId: ProjectId, reportFormat: ReportFormat = ReportFormat.All): ScanSettings = ScanSettings(app, appName, paths.appData / "dependency-check" / "projects" / projectId.num.toString, reportFormat)
}
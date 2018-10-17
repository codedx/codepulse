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
import org.owasp.dependencycheck.Engine
import org.owasp.dependencycheck.utils.{ Settings => DepCheckSettings }
import com.secdec.codepulse.data.model.ProjectId
import com.secdec.codepulse.paths
import com.secdec.codepulse.tracer.projectDataProvider
import com.secdec.codepulse.util.Implicits._
import net.liftweb.util.Helpers.AsInt

sealed abstract class ReportFormat(val value: String)
object ReportFormat {
	case object Xml extends ReportFormat("XML")
	case object Html extends ReportFormat("HTML")
	case object Vuln extends ReportFormat("VULN")
	case object Json extends ReportFormat("JSON")
	case object Csv extends ReportFormat("CSV")
	case object All extends ReportFormat("ALL")
}

sealed trait ApplicableSettings {
	def settings: DepCheckSettings

	def withEngine[T](f: Engine => T): T = {
		val engine = new Engine(settings)
		try {
			f(engine)
		} finally {
			engine.close()
		}
	}
}

case class Settings(
	dataDir: File
) extends ApplicableSettings {

	val settings = new DepCheckSettings

	settings.setString(DepCheckSettings.KEYS.DATA_DIRECTORY, dataDir.getAbsolutePath)
	settings.setBoolean(DepCheckSettings.KEYS.ANALYZER_JAR_ENABLED, true)
	settings.setBoolean(DepCheckSettings.KEYS.ANALYZER_ARCHIVE_ENABLED, true)
	settings.setBoolean(DepCheckSettings.KEYS.ANALYZER_NEXUS_ENABLED, true)
	settings.setBoolean(DepCheckSettings.KEYS.ANALYZER_NEXUS_USES_PROXY, false)
}

object Settings {
	lazy val defaultDataDir = paths.localAppData / "dependency-check" / "data"

	implicit def defaultSettings = Settings(dataDir = defaultDataDir)
}

case class ScanSettings(
	app: File, appName: String,
	reportDir: File, reportFormat: ReportFormat
)

object ScanSettings {
	def apply(app: File, appName: String, identifier: String, reportFormat: ReportFormat = ReportFormat.All): ScanSettings = ScanSettings(app, appName, paths.appData / "owasp-dependency-check" / "projects" / identifier, reportFormat)
	def createFromProject(identifier: String, app: File): ScanSettings = {
		val project = projectDataProvider getProject ProjectId(identifier.toInt)
		val name = project.metadata.name
		ScanSettings(app, name, identifier)
	}
}
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

package com.secdec

import java.text.SimpleDateFormat
import java.util.Properties
import scala.collection.JavaConverters.propertiesAsScalaMapConverter
import scala.util.Try
import com.secdec.codepulse.util.ApplicationData
import com.secdec.codepulse.util.Implicits._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File
import com.typesafe.config.ConfigRenderOptions
import java.io.FileWriter
import com.typesafe.config.ConfigValueFactory

/** @author dylanh
  *
  */
package object codepulse {
	object version {
		private case class VersionInfo(version: String, releaseDate: String)
		private lazy val versionInfo = {
			val vp = Try {
				val vp = new Properties
				vp.load(getClass.getClassLoader.getResourceAsStream("version.properties"))

				import collection.JavaConverters._
				vp.asScala
			}

			(for {
				props <- vp.toOption
				version <- props.get("version")
				releaseDate <- props.get("releaseDate")
			} yield {
				VersionInfo(version, releaseDate)
			}) getOrElse VersionInfo("<unknown>", "<unknown>")
		}

		lazy val number = versionInfo.version
		lazy val date = Try { new SimpleDateFormat("M/d/yyyy").parse(versionInfo.releaseDate) }.toOption
		lazy val dateRaw = versionInfo.releaseDate
	}

	object paths {
		val appData = ApplicationData.getApplicationDataFolder("Code Dx", "Code Pulse", "codepulse")
		val localAppData = ApplicationData.getLocalApplicationDataFolder("Code Dx", "Code Pulse", "codepulse")
		val logFiles = appData / "log-files"

		logFiles.mkdirs
	}

	object userSettings {
		private val configFile = paths.appData / "codepulse.conf"
		private var config =
			ConfigFactory
				.parseFile(configFile)
				.withFallback(ConfigFactory.load())
				.withOnlyPath("cp")

		def tracePort = config.getInt("cp.trace_port")
		def tracePort_=(newPort: Integer) = {
			config = config.withValue("cp.trace_port", ConfigValueFactory.fromAnyRef(newPort, "specified value"))
			saveToAppData
			newPort
		}

		if (!configFile.exists())
			saveToAppData

		def saveToAppData() {
			val renderOptions = ConfigRenderOptions.defaults.setJson(false).setOriginComments(false)
			val output = config.root.render(renderOptions)

			val writer = new FileWriter(configFile)
			writer.write(output)
			writer.close()
		}
	}
}
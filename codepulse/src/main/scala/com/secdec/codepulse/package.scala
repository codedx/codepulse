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
import ch.qos.logback.classic.Level
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

	val maxInstanceNameEnvLength = 50
	val instanceNameEnvVar = "CODE_PULSE_INSTANCE_NAME"
	val instanceName: String = sys.env.get(instanceNameEnvVar).getOrElse("").replaceAll("[^A-Za-z0-9_-]", "_").take(maxInstanceNameEnvLength)

	object paths {

		val appData = ApplicationData.getApplicationDataFolder("Code Dx", "Code Pulse", "codepulse", codepulse.instanceName)
		val localAppData = ApplicationData.getLocalApplicationDataFolder("Code Dx", "Code Pulse", "codepulse", codepulse.instanceName)
		val logFiles = appData / "log-files"

		logFiles.mkdirs
	}

	object userSettings {
		private val configFile = paths.appData / "codepulseSettings.conf"
		private var config =
			ConfigFactory
				.parseFile(configFile)
  		  .resolve()
				.withFallback(ConfigFactory.load())
				.withOnlyPath("cp.userSettings") // do not return systemSettings, which could get persisted to disk later on

		def tracePort = config.getInt("cp.userSettings.tracePort")
		def tracePort_=(newPort: Integer) = {
			config = config.withValue("cp.userSettings.tracePort", ConfigValueFactory.fromAnyRef(newPort, "specified value"))
			saveToAppData
			newPort
		}

		def symbolServicePort = config.getString("cp.userSettings.symbolService.port")

		def secdecLoggingLevel: Option[Level] = {
			getLogLevel(config, "cp.userSettings.logging.secdecLoggingLevel")
		}

		def codedxLoggingLevel: Option[Level] = {
			getLogLevel(config, "cp.userSettings.logging.codedxLoggingLevel")
		}

		def bootstrapLoggingLevel: Option[Level] = {
			getLogLevel(config, "cp.userSettings.logging.bootstrapLoggingLevel")
		}

		def liftwebLoggingLevel: Option[Level] = {
			getLogLevel(config, "cp.userSettings.logging.liftwebLoggingLevel")
		}

		def rootLoggingLevel: Option[Level] = {
			getLogLevel(config, "cp.userSettings.logging.rootLoggingLevel")
		}

		private def getLogLevel(config: Config, setting: String): Option[Level] = {
			if (!config.hasPath(setting)) return None

			var logLevel = config.getString(setting)
			if (logLevel == null) return None

			logLevel = logLevel.trim().toUpperCase()
			if (logLevel.isEmpty()) return None

			logLevel match {
				case "OFF" => Option(Level.OFF)
				case "ERROR" => Option(Level.ERROR)
				case "WARN" => Option(Level.WARN)
				case "INFO" => Option(Level.INFO)
				case "DEBUG" => Option(Level.DEBUG)
				case "TRACE" => Option(Level.TRACE)
				case "ALL" => Option(Level.ALL)
				case _ => None
			}
		}

		if (!configFile.exists())
			saveToAppData

		def saveToAppData() {
			val renderOptions = ConfigRenderOptions.defaults.setJson(false).setOriginComments(false)
			val output = config.root.render(renderOptions)

			// preserve option to override tracePort via an environment variable
			val tracePortPattern = """(tracePort="?\d+"?)""".r
			val outputWithTracePort = tracePortPattern.replaceFirstIn(output, "$1\n" + " " * 12 + "tracePort=\\${?CODE_PULSE_TRACE_PORT}")

			// preserve option to override port for symbol service via an environment variable
			val symbolServicePortPattern = """(port="?\d+"?)""".r
			val outputWithServicePortPattern = symbolServicePortPattern.replaceFirstIn(outputWithTracePort, "$1\n" + " " * 16 + "port=\\${?SYMBOL_SERVICE_PORT}")

			val writer = new FileWriter(configFile)
			writer.write(outputWithServicePortPattern)
			writer.close()
		}
	}

	object systemSettings {
		private var config = ConfigFactory.load().withOnlyPath("cp.systemSettings")

		def symbolServiceBinary = config.getString("cp.systemSettings.symbolService.binary")
		def symbolServiceLocation = config.getString("cp.systemSettings.symbolService.location")
	}
}
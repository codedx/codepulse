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

import sbt._
import Keys._
import sbt.Defaults.forDependencies
import BuildKeys._

trait VersionSystem {
	/** Add these settings to a project to generate EditionVersion.scala */
	def versionSettings: Seq[Setting[_]] = Seq(versionGeneratorSetting)

	private val versionGeneratorSetting = {
		resourceGenerators in Compile <+= (resourceManaged in Compile, version, releaseDate) map { (resources, version, date) =>
			val versionFile = resources / "version.properties"

			val versionFileContents =
			"""|version=%s
			   |releaseDate=%s
			""".stripMargin.format(version, date)

			IO.write(versionFile, versionFileContents)

			mappings in (Compile, packageBin) += versionFile -> "version.properties"

			Seq(versionFile)
		}
	}
}
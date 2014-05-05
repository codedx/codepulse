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

/** Helper for included embedded tools, as downloaded by DependencyFetcher, as
  * generated resources.
  *
  * @author robertf
  */
object EmbeddedToolResources extends BuildExtra {

	import DependencyFetcher.Keys.{ Dependencies, dependencyCheck }

	object Keys {
		val EmbeddedTools = config("embedded-tools")

		val dependencyCheckFolder = SettingKey[String]("dependency-check-folder")
	}

	import Keys._

	lazy val embeddedToolSettings: Seq[Setting[_]] = Seq(
		dependencyCheckFolder in EmbeddedTools := "tools/dependency-check",

		resourceGenerators in Compile <+= (streams, cacheDirectory in Compile, resourceManaged in Compile, dependencyCheck in Dependencies, dependencyCheckFolder in EmbeddedTools) map { (s, cache, managedRes, depCheck, depCheckFolder) =>
			if (!depCheck.exists)
				s.log.warn("Missing dependency check. Please run `fetch-runtime-dependencies` or download and place in " + depCheck + ".")

			val folder = managedRes / depCheckFolder
			val mappings = depCheck.*** x rebase(depCheck, folder)

			Sync(cache / "embedded-tools")(mappings)

			mappings.map(_._2)
		}
	)
}
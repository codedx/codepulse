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

object FetchCache {

	/** Setting for the "fetch cache" directory.
	  * The fetch cache is meant to be used by tasks that fetch (download)
	  * some external resource that will be used by part of the build or
	  * packaging process. Current examples include the "distrib dependencies"
	  * task which downloads JVM distributions for various operating systems,
	  * and the Brakeman gem fetcher, which downloads the Ruby gems for brakeman.
	  *
	  * This setting is controlled by a default value and an optional override
	  * value. By using `FetchCache.settings` in a project, the default directory
	  * will be the 'fetch-cache' dir in the root of the repository, and the
	  * override will be activated if the "fetchcache.override" system
	  * property is set.
	  */
	val fetchCacheDir = SettingKey[File]("fetch-cache-dir")

	val fetchCacheDefaultDir = SettingKey[File]("fetch-cache-default-dir")
	val fetchCacheOverrideDir = SettingKey[Option[File]]("fetch-cache-override-dir")

	lazy val settings = Seq(

		// By default, the fetch cache is located in the hgroot under 'fetch-cache'
		fetchCacheDefaultDir := file("./fetch-cache"),

		// An override to the fetch cache is specified with the
		// "fetchcache.override" system property.
		fetchCacheOverrideDir := {
			Option{ System getProperty "fetchcache.override" } map { file(_) }
		},

		// The actual fetch cache will be equal to the default dir setting, unless the
		// override dir is specified as a non-None value.
		fetchCacheDir <<= (fetchCacheDefaultDir, fetchCacheOverrideDir) { (defaultDir, overrideDir) =>
			overrideDir getOrElse defaultDir
		}
	)
}
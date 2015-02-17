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

object PluginDef extends Build {
	// see http://stackoverflow.com/questions/8568821/in-sbt-how-do-you-add-a-plugin-thats-in-the-local-filesystem
	lazy override val projects = Seq(root)
	lazy val root = Project("plugins", file(".")).dependsOn(betterzipPlugin).settings(
		sbt.Keys.libraryDependencies += "org.apache.commons" % "commons-compress" % "1.6",
		sbt.Keys.libraryDependencies += "commons-io" % "commons-io" % "2.1"
	)
	lazy val betterzipPlugin = RootProject(file("sbt-betterzip"))
}
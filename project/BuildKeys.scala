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

object BuildKeys {

	val releaseDate = SettingKey[String]("release-date")

	val packageEmbeddedWin32 = TaskKey[File]("package-embedded-win32", "Creates a ZIP distribution of the node-webkit embedded version of the current project for Windows (32-bit)")
	val packageEmbeddedOsx = TaskKey[File]("package-embedded-osx", "Creates a ZIP distribution of the node-webkit embedded version of the current project for OS X (32/64-bit)")
	val packageEmbeddedLinuxX86 = TaskKey[File]("package-embedded-linux-x86", "Creates a ZIP distribution of the node-webkit embedded version of the current project for Linux (x86)")
}
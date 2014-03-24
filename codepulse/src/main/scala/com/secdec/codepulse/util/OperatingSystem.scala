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

package com.secdec.codepulse.util

sealed trait OperatingSystem

/** Helper object for determining the current operating system */
object OperatingSystem {
	/** Microsoft Windows */
	case object Windows extends OperatingSystem

	/** Apple OS X */
	case object OSX extends OperatingSystem

	/** Linux */
	case object Linux extends OperatingSystem

	/** Other (non-OSX/non-Linux) variant of Unix */
	case object Unix extends OperatingSystem

	/** Unknown operating system */
	case object Unknown extends OperatingSystem

	private lazy val currentOS: OperatingSystem = {
		// comparing after toLowerCase isn't the best, but OS names aren't localized, so it works
		val osName = System.getProperty("os.name").toLowerCase
		val osVersion = System.getProperty("os.version")

		if (osName.startsWith("windows"))
			Windows
		else if ((osName.startsWith("mac") || osName.startsWith("darwin")) && osVersion.startsWith("10."))
			OSX
		else if (osName.startsWith("linux"))
			Linux
		else if (osName.startsWith("sunos"))
			Unix
		else
			Unknown
	}

	/** Returns the current `OperatingSystem` */
	def current = currentOS
}

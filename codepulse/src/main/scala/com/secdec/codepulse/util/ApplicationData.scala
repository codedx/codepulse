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

import java.io.File

/** Helper object for figuring out an appropriate application data folder,
  * based on the current platform.
  */
object ApplicationData {
	import RichFile._

	private def getAppData(companyName: String, appName: String, appShortName: String, local: Boolean) = OperatingSystem.current match {
		case OperatingSystem.Windows =>
			val appData = if (local) WindowsHelper.localAppDataFolder else WindowsHelper.appDataFolder
			if (appData == null)
				throw new IllegalStateException("Could not find application data folder")

			new File(appData) / companyName / appName

		case OperatingSystem.OSX =>
			val home = System.getProperty("user.home")
			if (home == null)
				throw new IllegalStateException("Could not find user home folder")

			new File(home) / "Library" / "Application Support" / appName

		case OperatingSystem.Linux | OperatingSystem.Unix =>
			val home = System.getProperty("user.home")
			if (home == null)
				throw new IllegalStateException("Could not find user home folder")

			new File(home) / s".$appShortName"

		case _ =>
			throw new NotImplementedError
	}

	def getApplicationDataFolder(companyName: String, appName: String, appShortName: String) = getAppData(companyName, appName, appShortName, false)
	def getLocalApplicationDataFolder(companyName: String, appName: String, appShortName: String) = getAppData(companyName, appName, appShortName, true)

	/** Internal helper for Windows to query for appdata folder the proper way */
	private object WindowsHelper {
		import com.sun.jna._
		import com.sun.jna.win32._
		import com.sun.jna.platform.win32._

		import scala.collection.immutable.HashMap
		import scala.collection.JavaConversions._

		private def getFolderPath(folder: Int) = {
			val path = new Array[Char](WinDef.MAX_PATH)
			val result = Shell32.INSTANCE.SHGetFolderPath(null, folder, null, ShlObj.SHGFP_TYPE_CURRENT, path)

			if (result == WinError.S_OK) {
				val pathStr = new String(path)
				pathStr.substring(0, pathStr.indexOf(0))
			} else
				null
		}

		lazy val appDataFolder = getFolderPath(ShlObj.CSIDL_APPDATA)
		lazy val localAppDataFolder = getFolderPath(ShlObj.CSIDL_LOCAL_APPDATA)
	}
}

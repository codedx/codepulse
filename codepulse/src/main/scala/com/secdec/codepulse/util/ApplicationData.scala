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
	def getApplicationDataFolder(companyName: String, appName: String, appShortName: String) = OperatingSystem.current match {
		case OperatingSystem.Windows =>
			val appData = WindowsHelper.appDataFolder
			if (appData == null)
				throw new IllegalStateException("Could not find application data folder")

			val companyFolder = new File(appData, companyName)
			val dataFolder = new File(companyFolder, appName)

			dataFolder

		case OperatingSystem.OSX =>
			val home = System.getProperty("user.home")
			if (home == null)
				throw new IllegalStateException("Could not find user home folder")

			val appSupport = new File(home, "Library/Application Support")
			val dataFolder = new File(appSupport, appName)

			dataFolder

		case OperatingSystem.Linux | OperatingSystem.Unix =>
			val home = System.getProperty("user.home")
			if (home == null)
				throw new IllegalStateException("Could not find user home folder")

			val dataFolder = new File(home, s".$appShortName")

			dataFolder

		case _ =>
			throw new NotImplementedError
	}

	/** Internal helper for Windows to query for appdata folder the proper way */
	private object WindowsHelper {
		import com.sun.jna._
		import com.sun.jna.win32._

		import scala.collection.immutable.HashMap
		import scala.collection.JavaConversions._

		// based on the code from http://stackoverflow.com/a/586917/1620756
		class HANDLE extends PointerType with NativeMapped
		class HWND extends HANDLE

		lazy val options = HashMap(
			Library.OPTION_TYPE_MAPPER -> W32APITypeMapper.UNICODE,
			Library.OPTION_FUNCTION_MAPPER -> W32APIFunctionMapper.UNICODE)

		trait Shell32 extends Library {
			/** see http://msdn.microsoft.com/en-us/library/bb762181(VS.85).aspx
			  *
			  * HRESULT SHGetFolderPath( HWND hwndOwner, int nFolder, HANDLE hToken,
			  * DWORD dwFlags, LPTSTR pszPath);
			  */
			def SHGetFolderPath(hwndOwner: HWND, nFolder: Integer, hToken: HANDLE, dwFlags: Integer, pszPath: Array[Char]): Integer
		}

		object Shell32 {
			val MAX_PATH = 260
			val CSIDL_APPDATA = 0x001a
			val SHGFP_TYPE_CURRENT = 0
			val SHGFP_TYPE_DEFAULT = 1
			val S_OK = 0

			lazy val instance = Native.loadLibrary("shell32", classOf[Shell32], options).asInstanceOf[Shell32]
		}

		lazy val appDataFolder = {
			val path = new Array[Char](Shell32.MAX_PATH)
			val result = Shell32.instance.SHGetFolderPath(null, Shell32.CSIDL_APPDATA, null, Shell32.SHGFP_TYPE_CURRENT, path)

			if (result == Shell32.S_OK) {
				val pathStr = new String(path)
				pathStr.substring(0, pathStr.indexOf(0))
			} else
				null
		}
	}
}

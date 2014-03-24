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

package com.secdec.codepulse.components.version.snippet

import scala.xml.NodeSeq
import net.liftweb.util.BindHelpers._
import java.text.SimpleDateFormat
import com.secdec.codepulse.version
import net.liftweb.http.DispatchSnippet

/** Snippet for displaying Swavis version number-related properties
  * @author dylanh
  */
class VersionSnippet extends DispatchSnippet {

	def dateFormat = new SimpleDateFormat("M/d/yyyy")

	def dispatch = {
		case "render" => (xhtml: NodeSeq) => bind("v", xhtml,
			"number" -> version.number,
			"date" -> dateFormat.format(version.date))
	}
}
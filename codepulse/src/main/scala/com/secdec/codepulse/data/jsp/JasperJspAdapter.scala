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

package com.secdec.codepulse.data.jsp

import com.secdec.codepulse.data.bytecode.CodeForestBuilder
import java.io.File
import com.secdec.codepulse.data.bytecode.CodeTreeNode

/** Data adapter to help populate `CodeForestBuilder` with JSP info.
  * This implementation is specific to what Jasper does for JSP.
  *
  * @author robertf
  */
class JasperJspAdapter extends JspAdapter {
	private val jsps = List.newBuilder[String]
	private val webinfs = List.newBuilder[String]

	def addJsp(path: String) = jsps += path
	def addWebinf(path: String) = webinfs += path

	def build(codeForestBuilder: CodeForestBuilder): List[(String, Int)] = {
		val jsps = this.jsps.result
		val webinfs = this.webinfs.result

		val jspClasses = Set.newBuilder[String]

		for (webinf <- webinfs) {
			val parent = Option(new File(webinf).getParent) getOrElse ""
			val parentLen = parent.length
			jspClasses ++= jsps
				.filter(_.startsWith(parent))
				.map(_.substring(parentLen))
		}

		// build up
		jspClasses.result.toList map { clazz =>
			val jspClassName = JasperUtils.makeJavaClass(clazz)
			val displayName = clazz.split('/').filter(!_.isEmpty).toList
			val node = codeForestBuilder.getOrAddJsp(displayName, 10)
			jspClassName -> node.id
		}
	}
}
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
import com.secdec.codepulse.input.pathnormalization.{ FilePath, PathNormalization }

/** Data adapter to help populate `CodeForestBuilder` with JSP info.
  * This implementation is specific to what Jasper does for JSP.
  *
  * @author robertf
  */
class JasperJspAdapter extends JspAdapter {
	private val jsps = List.newBuilder[(String, String, Int)]
	private val webinfs = List.newBuilder[String]

	def addJsp(path: String, entryName: String, size: Int) = jsps += ((path, entryName, size))
	def addWebinf(path: String) = webinfs += path

	def build(codeForestBuilder: CodeForestBuilder): List[(String, Int)] = {
		val jsps = this.jsps.result
		val webinfs = this.webinfs.result

		val jspClasses = Set.newBuilder[(String, String, Int)]

		for (webinf <- webinfs) {
			val parent = Option(new File(webinf).getParent) getOrElse ""
			val parentLen = parent.length
			jspClasses ++= jsps
				.filter(_._2.startsWith(parent))
				.map { case (path, entryName, size) => (path, entryName.substring(parentLen), size) }
		}

		val sourceFiles = jsps map {
			case (path, _, _) => path
		}

		codeForestBuilder.addExternalSourceFiles(CodeForestBuilder.JSPGroupName, sourceFiles)

		val filePaths = sourceFiles.flatMap(FilePath(_)).sortWith(_.toString.length < _.toString.length)

		// build up - class name must match generated class name supporting JSP file; otherwise,
		// the class name will not match the inclusion filter provided to the Java tracer
		jspClasses.result.toList map {
			case (path, entryName, size) =>
				val jspClassName = JasperUtils.makeJavaClass(entryName)
				val displayName = entryName.split('/').filter(!_.isEmpty).toList
				val authority = FilePath(path) match {
					case Some(cz) => filePaths.find(PathNormalization.isLocalizedInAuthorityPath(_, cz))
					case None => None
				}

				val node = authority match {
					case Some(a) => codeForestBuilder.getOrAddJsp(displayName, size, Some(a.toString), None) // TODO: Get JSP source location count
					case None => codeForestBuilder.getOrAddJsp(displayName, size, Some(path), None)

				}

				jspClassName -> node.id
		}
	}
}
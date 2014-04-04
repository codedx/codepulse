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

import com.secdec.codepulse.data.trace.TreeNodeDataAccess

/** Handles mapping of Jasper JSP method signatures down to their JSP node.
  *
  * @param basePackage the base package for the JSPs (e.g., org.apache.jsp)
  * @param nodeData `TreeNodeDataAccess` to map against
  *
  * @author robertf
  */
class JasperJspMapper(basePackage: String, nodeData: TreeNodeDataAccess) extends JspMapper {
	private val basePrefix = s"${basePackage.replace('.', '/')}/"
	private lazy val basePrefixLength = basePrefix.length

	def map(signature: String): Option[Int] = if (signature startsWith basePrefix) {
		val className = signature.drop(basePrefixLength).takeWhile(_ != '.').replace('/', '.')
		nodeData.getNodeIdForJsp(className)
	} else None

	def getInclusion(jspClass: String) = s"^$basePrefix${jspClass.replace('.', '/')}\\..*"
}

object JasperJspMapper {
	val DefaultBasePackage = "org.apache.jsp"
	def apply(nodeData: TreeNodeDataAccess): JasperJspMapper = apply(DefaultBasePackage, nodeData)
	def apply(basePackage: String, nodeData: TreeNodeDataAccess): JasperJspMapper = new JasperJspMapper(basePackage, nodeData)
}
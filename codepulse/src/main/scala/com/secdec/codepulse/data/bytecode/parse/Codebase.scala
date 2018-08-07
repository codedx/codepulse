/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */
package com.secdec.codepulse.data.bytecode.parse

import com.secdec.codepulse.data.bytecode.parse.StringConverters.AsInt

sealed abstract class CodebaseType(val keyword: String)
object CodebaseType {
	case object Java extends CodebaseType("java")
	case object Jsp extends CodebaseType("jsp")

	def unapply(keyword: String) = keyword match {
		case "java" => Some(Java)
		case "jsp" => Some(Jsp)
		case _ => None
	}

	def fromKeyword(keyword: String) = unapply(keyword).getOrElse {
		throw new IllegalArgumentException(s"Invalid CodebaseType keyword: $keyword")
	}

}

case class CodebaseMemoParts(codebaseType: CodebaseType, version: Int, body: String)
object CodebaseMemoParts {
	private val MemoStructure = raw"\[(.*):v(\d+)\](.*)".r

	def fromRaw(raw: String) = raw match {
		case MemoStructure(CodebaseType(t), AsInt(version), body) => Some(CodebaseMemoParts(t, version, body))
		case _ => None
	}

	def buildRaw(codebaseType: CodebaseType, version: Int, appendBody: StringBuilder => Unit) = {
		val sb = new StringBuilder("[")
		sb append codebaseType.keyword
		sb append ":v"
		sb append version
		sb append ']'
		// append the 'body', then return
		appendBody(sb)
		sb.result()
	}
}
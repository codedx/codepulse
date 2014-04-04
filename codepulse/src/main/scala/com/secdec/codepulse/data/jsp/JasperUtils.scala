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

/** Utilities for Jasper interaction. Most of the ideas here are taken from Jasper's
  * source (most notably, JspUtil and JspCompilationContext). A lot of the code within
  * was simply translated directly from Java to Scala.
  */
private[jsp] object JasperUtils {
	private val JavaKeywords = Set(
		"abstract", "assert", "boolean", "break", "byte", "case", "catch",
		"char", "class", "const", "continue", "default", "do", "double",
		"else", "enum", "extends", "final", "finally", "float", "for",
		"goto", "if", "implements", "import", "instanceof", "int",
		"interface", "long", "native", "new", "package", "private",
		"protected", "public", "return", "short", "static", "strictfp",
		"super", "switch", "synchronized", "this", "throw", "throws",
		"transient", "try", "void", "volatile", "while")

	def isJavaKeyword(key: String) = JavaKeywords.contains(key)

	def mangleChar(ch: Char) = {
		val result = new Array[Char](5)
		result(0) = '_'
		result(1) = Character.forDigit((ch >> 12) & 0xf, 16)
		result(2) = Character.forDigit((ch >> 8) & 0xf, 16)
		result(3) = Character.forDigit((ch >> 4) & 0xf, 16)
		result(4) = Character.forDigit((ch) & 0xf, 16)
		new String(result)
	}

	def makeJavaIdentifier(identifier: String, periodToUnderscore: Boolean = true) = {
		val modifiedIdentifier = new StringBuilder(identifier.length);
		if (!Character.isJavaIdentifierStart(identifier charAt 0))
			modifiedIdentifier.append('_');

		for (ch <- identifier) {
			if (Character.isJavaIdentifierPart(ch) &&
				(ch != '_' || !periodToUnderscore))
				modifiedIdentifier append ch
			else if (ch == '.' && periodToUnderscore)
				modifiedIdentifier append '_'
			else
				modifiedIdentifier append mangleChar(ch)
		}

		if (isJavaKeyword(modifiedIdentifier.toString()))
			modifiedIdentifier append '_'

		modifiedIdentifier.toString
	}

	def makeJavaClass(path: String) = path.split('/').filter(!_.isEmpty).map(makeJavaIdentifier(_)) mkString "."
}
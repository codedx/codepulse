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

/** Analyzer for JSP files. The purpose is to make a rough approximation of
  * how many instructions are contained in a JSP file.
  *
  * We do this by scanning through the file and first counting how many
  * statements are within. Then we apply a multiplier to the statement count
  * and add a constant overhead value to approximate the number of instructions
  * for the file.
  *
  * @author robertf
  */
object JspAnalyzer {
	val ConstantOverhead = 20
	val LineMultiplier = 1
	val StatementMultiplier = 2

	private val Block = (raw"(?s)<(%(?:=|!)?)(.*?)%>").r

	private val ExpressionBlock = "%="
	private val ScriptBlock = "%"
	private val DeclarationBlock = "%!"

	case class Result(
		totalLineCount: Int,
		approximateInstructionCount: Int,
		nonBlockLineCount: Int,
		statementCount: Int)

	def analyze(contents: String) = {
		val blocks = Block.findAllMatchIn(contents).toList
		val statementCount = blocks.map { m =>
			val statementCount = (m group 2).count(_ == ';')

			m group 1 match {
				case ExpressionBlock => 1 // count as 1 statement
				case ScriptBlock => 1 + statementCount // (1 + number of ';') statements
				case DeclarationBlock => statementCount // (number of ';') statements
				case _ => 0
			}
		}.sum

		val totalLineCount = contents.count(_ == '\n')
		val blockLineCount = blocks.map(_ group 2).map(_ count (_ == '\n')).sum
		val nonBlockLineCount = totalLineCount - blockLineCount

		val approximateInstructionCount = {
			ConstantOverhead + StatementMultiplier * statementCount + LineMultiplier * nonBlockLineCount
		}
		Result(
			totalLineCount,
			approximateInstructionCount,
			nonBlockLineCount,
			statementCount
		)
	}
}
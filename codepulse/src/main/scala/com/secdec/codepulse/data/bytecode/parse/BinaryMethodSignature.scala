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

sealed trait BinaryMethodSignature extends MethodSignature {
	def toMemo: BinaryMethodSignatureMemo
}
object BinaryMethodSignature {
	def fromMemo(memo: BinaryMethodSignatureMemo): BinaryMethodSignature = {
		CodebaseMemoParts.fromRaw(memo.raw) getOrElse { unrecognized(memo) } match {
			case CodebaseMemoParts(CodebaseType.Java, 1, body) => JavaBinaryMethodSignature.parseMemoV1(body)
			case _ => unrecognized(memo)
		}
	}

	@inline private def unrecognized(memo: BinaryMethodSignatureMemo): Nothing = {
		throw new IllegalArgumentException(s"Unrecognized binary method signature memo: ${memo.raw}")
	}
}

/** "MEMO" representation of a `BinaryMethodSignature`.
  *
  * Technically this is a value class wrapper for String, and it exists
  * simply for the purpose of providing a type-level hint about what the
  * String contains. Clients should not attempt to interact directly with
  * the `raw` data. Instead, they should call `MethodSignature.fromMemo`
  * to convert the memo to a nicer in-memory representation.
  *
  * @param raw
  */
final case class BinaryMethodSignatureMemo(raw: String) extends AnyVal

// -------------------------------------------------------------
//                           JAVA
// -------------------------------------------------------------

/** Represents a java method signature found via bytecode inspection.
  * Method signatures in this form can be converted to and from "memos"
  * in order to be stored in the database.
  */
case class JavaBinaryMethodSignature(
	access: AccessFlags,
	name: String,
	typeSignature: JVMMethodTypeSignature
) extends BinaryMethodSignature {

	def codebaseType: CodebaseType = CodebaseType.Java

	def simplifiedReturnType = SimplifiedType of typeSignature.returnType
	def simplifiedArgumentTypes = typeSignature.paramTypes.map(SimplifiedType.of)

	def toMemo = new BinaryMethodSignatureMemo(CodebaseMemoParts.buildRaw(codebaseType, 1, sb => {
		sb append access.bits
		sb append ';'
		sb append name
		sb append ';'
		sb append JVMSignatureConverter.toString(typeSignature)
	}))

	override def toString = {
		val sb = new StringBuilder
		sb append access.toStringForMethod
		sb += ' '
		if(typeSignature.typeParams.nonEmpty) sb append typeSignature.typeParams.mkString("<", ", ", "> ")
		sb append typeSignature.returnType
		sb += ' '
		sb append name
		sb append typeSignature.paramTypes.mkString("(", ", ", ")")
		if(typeSignature.exceptionTypes.nonEmpty) sb append typeSignature.exceptionTypes.mkString(" throws ", ", ", "")
		sb.result()
	}

}

object JavaBinaryMethodSignature {

	def parseMemoV1(body: String) = {
		// format is "<access>;<name>;<signature>"
		// note that signature will usually have ";" in it, so we limit the split to 3 parts
		val Array(accessStr, name, rawSignature) = body.split(";", 3)
		JavaBinaryMethodSignature(
			AccessFlags(accessStr.toInt),
			name,
			JVMSignatureConverter.fromString[JVMMethodTypeSignature](rawSignature)
		)
	}

}

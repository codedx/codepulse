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

/** Base trait describing "Binary Classes" in a codebase.
  * A class signature should typically include a name, and type signature where applicable,
  * but the actual details are up to the implementing classes.
  * The main focus of this base trait is converting class signatures to and from their "memoized" forms.
  * Classes stored in the database will have their "memo" in a dedicated column.
  */
sealed trait BinaryClassSignature {
	def codebaseType: CodebaseType
	def toMemo: BinaryClassSignatureMemo
}

object BinaryClassSignature {
	def fromMemo(memo: BinaryClassSignatureMemo): BinaryClassSignature = {
		CodebaseMemoParts.fromRaw(memo.raw) getOrElse { unrecognized(memo) } match {
			case CodebaseMemoParts(CodebaseType.Java, 1, body) => JavaBinaryClassSignature.parseMemoV1(body)
			case _ => unrecognized(memo)
		}
	}

	@inline private def unrecognized(memo: BinaryClassSignatureMemo) = {
		throw new IllegalArgumentException(s"Unrecognized binary class signature memo: ${memo.raw}")
	}
}

/** "MEMO" representation of a `BinaryClassDeclaration`.
  *
  * Technically this is a value class wrapper for String, and it exists
  * simply for the purpose of providing a type-level hint about what the
  * String contains. Clients should not attempt to interact directly with
  * the `raw` data. Instead they should call `BinaryClassDeclaration.fromMemo` to
  * convert the memo to a nicer in-memory representation.
  */
final case class BinaryClassSignatureMemo(val raw: String) extends AnyVal

// -------------------------------------------------------------
//                           JAVA
// -------------------------------------------------------------

case class JavaBinaryClassSignature(
	access: AccessFlags,
	name: ClassName,
	signature: JVMClassSignature
) extends BinaryClassSignature {

	def codebaseType: CodebaseType = CodebaseType.Java

	def toMemo = new BinaryClassSignatureMemo(CodebaseMemoParts.buildRaw(codebaseType, 1, sb => {
		sb append access.bits
		sb append ';'
		sb append name.slashedName
		sb append ';'
		sb append JVMSignatureConverter.toString(signature)
	}))

	override def toString = {
		val sb = new StringBuilder
		sb append access.toStringForClass
		if(sb.nonEmpty) sb += ' '
		sb append name.dottedName
		if(signature.typeParams.nonEmpty) sb append signature.typeParams.mkString("<", ", ", ">")
		sb append " extends " append signature.superClass
		if(signature.interfaces.nonEmpty) sb append signature.interfaces.mkString(" implements ", ", ", "")
		sb.result()
	}
}

object JavaBinaryClassSignature {
	def parseMemoV1(body: String) = {
		// format is "<access>;<slashedName>;<signature>"
		// note that signature will usually have ";" in it, so we limit the split to 3 parts
		val Array(accessStr, slashedName, rawSignature) = body.split(";", 3)
		JavaBinaryClassSignature(
			AccessFlags(accessStr.toInt),
			ClassName.fromSlashed(slashedName),
			JVMSignatureConverter.fromString[JVMClassSignature](rawSignature)
		)
	}
}

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

sealed trait SourceMethodSignature extends MethodSignature {
	def toMemo: SourceMethodSignatureMemo
}

object SourceMethodSignature {
	def fromMemo(memo: SourceMethodSignatureMemo): SourceMethodSignature = {
		CodebaseMemoParts.fromRaw(memo.raw) getOrElse { unrecognized(memo) } match {
			case CodebaseMemoParts(CodebaseType.Java, 1, body) => JavaSourceMethodSignature.parseMemoV1(body)
			case _ => unrecognized(memo)
		}
	}

	@inline private def unrecognized(memo: SourceMethodSignatureMemo) = {
		throw new IllegalArgumentException(s"Unrecognized source method signature memo: ${memo.raw}")
	}
}

/** "MEMO" representation of a `SourceMethodSignature`.
  *
  * Technically this is a value class wrapper for a `String`, and it exists
  * simply for the purpose of providing a type-level hint about what the
  * String contains. Clients should not attempt to interact directly with
  * the `raw` data. Instead, they should call `SourceMethodSignature.fromMemo`
  * to convert the memo to a nicer in-memory representation.
  *
  * @param raw
  */
final case class SourceMethodSignatureMemo(raw: String) extends AnyVal

// -------------------------------------------------------------
//                           JAVA
// -------------------------------------------------------------

case class JavaSourceMethodSignature(
	modifiers: AccessFlags,
	returnType: SourceTypeReference,
	name: String,
	argumentTypes: List[SourceTypeReference]
) extends SourceMethodSignature {
	def codebaseType = CodebaseType.Java

	def simplifiedReturnType = SimplifiedType of returnType
	def simplifiedArgumentTypes = argumentTypes map { SimplifiedType.of }

	def toMemo = SourceMethodSignatureMemo(CodebaseMemoParts.buildRaw(codebaseType, 1, sb => {
		sb append modifiers.bits
		sb append ';'
		sb append name
		sb append ';'
		sb append returnType.ref
		for(argType <- argumentTypes){
			sb append ';'
			sb append argType.ref
		}
	}))
}

object JavaSourceMethodSignature {
	def parseMemoV1(body: String): JavaSourceMethodSignature = {
		// assume the same 'chunk' structure as the .toMemo method would create
		val chunks = body.split(';').toList
		val rawMods :: name :: rawReturnType :: rawArgumentTypes = chunks

		// reconstruct the fields from the chunks
		val modifiers = AccessFlags(rawMods.toInt)
		val returnType = SourceTypeReference(rawReturnType)
		val paramTypes = rawArgumentTypes.map(SourceTypeReference(_))
		JavaSourceMethodSignature(modifiers, returnType, name, paramTypes)
	}
}

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

sealed trait SourceClassSignature {
	def codebaseType: CodebaseType
	def toMemo: SourceClassSignatureMemo
}
object SourceClassSignature {
	def fromMemo(memo: SourceClassSignatureMemo): SourceClassSignature = {
		CodebaseMemoParts.fromRaw(memo.raw) getOrElse { unrecognized(memo) } match {
			case CodebaseMemoParts(CodebaseType.Java, 1, body) => JavaSourceClassSignature.parseMemoV1(body)
			case _ => unrecognized(memo)
		}
	}

	@inline private def unrecognized(memo: SourceClassSignatureMemo) = {
		throw new IllegalArgumentException(s"Unrecognized source class signature memo: ${memo.raw}")
	}
}

final case class SourceClassSignatureMemo(raw: String) extends AnyVal

// -------------------------------------------------------------
//                           JAVA
// -------------------------------------------------------------

case class JavaSourceClassSignature(modifiers: AccessFlags, kind: TypeKind, name: ClassName, tParams: List[String], ext: List[SourceTypeReference], impl: List[SourceTypeReference]) extends SourceClassSignature {
	def codebaseType = CodebaseType.Java

	def toMemo = SourceClassSignatureMemo(CodebaseMemoParts.buildRaw(codebaseType, 1, sb => {
		var wroteFirst = false
		def segment(s: String) = {
			assert(!s.contains(';'), "SourceClassSignature memo conversion can't contain ';' in its segments")
			if(wroteFirst) {
				sb append ';'
			} else {
				wroteFirst = true
			}
			sb append s
		}

		// <modifiers>;<kind>;<name>[;<tParam>]*|[;<ext>]*|[;<impl>]*
		segment(modifiers.bits.toString)
		segment(kind.alias)
		segment(name.slashedName)
		// intersperse a "|" marker between each list for the signature types. That will be the list boundary symbol for parsing
		for(tParam <- tParams) segment(tParam)
		segment("|")
		for(typ <- ext) segment(typ.ref)
		segment("|")
		for(typ <- impl) segment(typ.ref)
	}))
}

object JavaSourceClassSignature {
	def parseMemoV1(body: String): JavaSourceClassSignature = {
		val chunks = body.split(';').toList
		val modifiersRaw :: kindAlias :: slashedName :: sigTypeChunks = chunks
		// the sigTypeChunks are actually three lists separated by "|" entries, so split them accordingly
		val (tParams, postTParamChunks) = sigTypeChunks.span(_ != "|")
		val (extChunks, postExtChunks) = postTParamChunks.tail.span(_ != "|")
		val implChunks = postExtChunks.tail

		val name = ClassName.fromSlashed(slashedName)
		val modifiers = AccessFlags(modifiersRaw.toInt)
		val kind = TypeKind.fromAlias(kindAlias)
		val extTypes = extChunks.map(SourceTypeReference(_))
		val implTypes = implChunks.map(SourceTypeReference(_))
		JavaSourceClassSignature(modifiers, kind, name, tParams, extTypes, implTypes)
	}
}

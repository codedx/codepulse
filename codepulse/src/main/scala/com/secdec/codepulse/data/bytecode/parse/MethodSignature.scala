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

import scala.util.parsing.combinator.JavaTokenParsers

/** Common base functionality between `BinaryMethodSignature` and `SourceMethodSignature` */
trait MethodSignature {
	def codebaseType: CodebaseType

	def name: String
	def simplifiedReturnType: SimplifiedType
	def simplifiedArgumentTypes: List[SimplifiedType]
}

/** A platform-agnostic, simplified, lossy encoding of a type.
  * This class serves as the common ground between source and binary representations
  * of the same types, i.e. for method signatures' return types and argument types.
  *
  * @param name The underlying string representing the simplified type
  */
case class SimplifiedType private[SimplifiedType](name: String) extends AnyVal
object SimplifiedType {
	/** Get the SimplifiedType of a full TypeSignature */
	def of(typeSignature: TypeSignature): SimplifiedType = {
		def simplify(sig: TypeSignature): String = sig match {
			case b: TypeSignature.BaseType => b.toString
			case TypeSignature.TypeVariable(name) => name
			case TypeSignature.ArrayType(inner) => simplify(inner) + "[]"
			case TypeSignature.ClassType(name, _, innerOpt) =>
				innerOpt.map(simplify).getOrElse {
					val s = name.slashedName
					val idx = s.lastIndexOf('/') max s.lastIndexOf('$')
					s.substring(idx + 1)
				}
		}
		SimplifiedType(simplify(typeSignature))
	}

	def of(sourceTypeReference: SourceTypeReference): SimplifiedType = {
		import SourceTypeParsing._
		parseAll(out, sourceTypeReference.ref) match {
			case Success(simplified, _) => SimplifiedType(simplified)
			case NoSuccess(msg, _) => throw new Exception(s"Failed to simplify $sourceTypeReference: $msg")
		}
	}
	private object SourceTypeParsing extends JavaTokenParsers {
		lazy val out: Parser[String] = ident ~ generic.? ~ arraySuffix ~ ('.' ~> out).? map {
			case frontIdent ~ genericPart ~ arr ~ tailIdent =>
				// if the tail is defined, use it. ignore the generic part. arraySuffix applies only to the front
				tailIdent getOrElse (frontIdent + arr)
		}
		lazy val generic = '<' ~> repsep(out | genericTypeBounded | "?", ",") <~ '>'
		lazy val genericTypeBounded = (ident | "?") ~> ("extends" | "super") ~> out
		val arraySuffix = literal("[]").* map {_.mkString}
	}
}

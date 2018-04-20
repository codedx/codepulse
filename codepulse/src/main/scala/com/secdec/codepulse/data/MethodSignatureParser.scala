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

package com.secdec.codepulse.data

import scala.util.parsing.combinator.RegexParsers
import MethodTypeParam._

/** A parser that understands MethodSignatures when they are in a form like:
  *
  * {{{
  * org/eclipse/equinox/launcher/Main.main;9;([Ljava/lang/String;)V
  * }}}
  *
  * This signature represents a MethodSignature where:
  *
  * {{{
  * name = "main"
  * className = "org.eclipse.equinox.launcher.Main"
  * modifiers = 9
  * params = List(ArrayType(ReferenceType("java.lang.String")))
  * returnType = PrimitiveType("Void")
  * }}}
  */
object MethodSignatureParser extends RegexParsers {

	/** Parser that accepts java identifier strings */
	def identifierPart = rep1 { acceptIf(Character.isJavaIdentifierPart _)(c => "Unexpected: " + c) } ^^ (_.mkString)

	/** A series of java identifiers, separated by "/" */
	def classIdentifier: Parser[String] = identifierPart ~ rep("/" ~ identifierPart) ^^ {
		case head ~ tail => tail.foldLeft(head) { case (accum, a ~ b) => accum + "." + b }
	}

	/** Either a single identifierPart, or "<init>" or "<clinit>" */
	def methodName: Parser[String] = identifierPart | "<init>" | "<clinit>"

	/** Parsers a series of digits as an integer */
	def number: Parser[Int] = rep1(elem("digit", _.isDigit)) ^^ (_.mkString.toInt)

	/** Parses a type parameter signature, as specified by the JVM. For details,
	  * see http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3
	  */
	def typeParam: Parser[MethodTypeParam] =
		"B" ^^^ Primitive("Byte") |
			"C" ^^^ Primitive("Char") |
			"D" ^^^ Primitive("Double") |
			"F" ^^^ Primitive("Float") |
			"I" ^^^ Primitive("Int") |
			"J" ^^^ Primitive("Long") |
			"S" ^^^ Primitive("Short") |
			"Z" ^^^ Primitive("Boolean") |
			"[" ~> typeParam ^^ { ArrayType(_) } |
			"L" ~> classIdentifier <~ ";" ^^ { ReferenceType(_) }

	/** Parses a series of type parameters, surrounded by parenthesis.
	  */
	def typeParamList: Parser[List[MethodTypeParam]] = "(" ~> typeParam.* <~ ")"

	/** Parses a return type signature, which is any of the signatures
	  * from `typeParam`, with the addition of Void as a valid type.
	  */
	def returnType = typeParam | "V" ^^^ Primitive("Void")

	/** Accepts a full method signature in the form of
	  * classIdentifier.methodName;modifiers;(paramsList)returnType
	  */
	def methodSignature(file: String) = {
		classIdentifier ~
			("." ~> methodName <~ ";") ~
			(number <~ ";") ~
			typeParamList ~
			returnType ^^
			{
				case clazz ~ name ~ flags ~ params ~ returnType =>
					MethodSignature(name, clazz, file, flags, params, returnType)
			}
	}

	/** Parses an entire MethodSignature, returning the result as an Option
	  * (instead of the `ParseResult` class).
	  */
	def parseSignature(signatureString: String, file: String): Option[MethodSignature] = {
		parse(methodSignature(file), signatureString) match {
			case Success(sig, _) => Some(sig)
			case _ => None
		}
	}
}
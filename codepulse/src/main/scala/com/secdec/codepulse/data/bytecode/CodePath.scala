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

package com.secdec.codepulse.data.bytecode

import com.secdec.codepulse.data.MethodSignatureParser
import com.secdec.codepulse.data.MethodSignature
import com.secdec.codepulse.data.MethodTypeParam
import java.lang.reflect.Modifier

/** Represents the Logical Location of a Method in a codebase.
  *
  * A CodePath generally starts with a Package (though it doesn't have to), followed by
  * any number of sub-packages, then a Class, followed by any number of inner classes,
  * and finally the method.
  *
  * CodePaths are represented as an ADT so that the compiler can impose restrictions;
  * for example, a Package path segment cannot be the child of a Class, nor can a Class
  * be a child of a Method.
  *
  * To represent the method `com.foo.bar.Main.Inner.bang()`, the following CodePath would be created:
  *
  * `Package("com", Package("foo", Package("bar", Class("Main", Class("Inner", Method("bang()"))))))`
  * A slightly more human-readable version of this would look like
  * `Package(com) -> Package(foo) -> Package(bar) -> Class(Main) -> Class(Inner) -> Method(bang())`
  */
sealed trait CodePath

object CodePath {
	sealed trait ChildOfPackage extends CodePath
	sealed trait ChildOfClass extends CodePath

	case class Package(name: String, child: ChildOfPackage) extends CodePath with ChildOfPackage
	case class Class(name: String, child: ChildOfClass) extends CodePath with ChildOfPackage with ChildOfClass
	case class Method(name: String) extends CodePath with ChildOfClass

	def parse(rawJvmSignature: String, file: Option[String]): Option[Package] = {
		MethodSignatureParser.parseSignature(rawJvmSignature, file) map { parse(_) }
	}

	def parse(methodSignature: MethodSignature): Package = {
		val (className :: packageParts) = methodSignature.containingClass.split('.').toList.reverse
		val classNameParts = className.split('$').toList.reverse

		val pMethod = Method(methodSignatureToString(methodSignature))

		val innerClass = Class(classNameParts.head, pMethod)
		val outerClass = classNameParts.tail.foldLeft(innerClass) { (inner, outerName) =>
			Class(outerName, inner)
		}

		packageParts match {
			case Nil => Package("<default package>", outerClass)
			case head :: tail =>
				val innerPackage = Package(head, outerClass)
				tail.foldLeft(innerPackage) { (accum, next) => Package(next, accum) }
		}
	}

	def typeParamToString(tp: MethodTypeParam): String = tp match {
		case MethodTypeParam.Primitive(name) => name.toLowerCase
		case MethodTypeParam.ReferenceType(ref) => ref.substring(1 + ref.lastIndexOf('.'))
		case MethodTypeParam.ArrayType(tp2) => typeParamToString(tp2) + "[]"
	}

	def methodSignatureToString(sig: MethodSignature): String = {
		val mods = Modifier.toString(sig.modifiers)
		val ret = typeParamToString(sig.returnType)
		val params = sig.params.map(typeParamToString).mkString(", ")
		s"$mods $ret ${sig.name}($params)"
	}
}
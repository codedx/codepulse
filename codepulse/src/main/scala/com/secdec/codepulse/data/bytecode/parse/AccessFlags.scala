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

import org.objectweb.asm.{Opcodes => O}

/** Typesafe wrapper around an integer value that represents
  * the combination of any number of `Opcodes.ACC_*` values.
  *
  * Note that not all of the flags provided by this class are
  * necessarily used by Code Dx. They are simply mapped 1:1
  * from what is found in the javadocs at
  * http://asm.ow2.org/asm50/javadoc/user/org/objectweb/asm/Opcodes.html
  *
  * The `toStringFor...` methods try to choose a "sane" set of
  * flags to present for stringification.
  */
case class AccessFlags(bits: Int) extends AnyVal {

	@inline def is(thoseBits: Int) = (bits & thoseBits) != 0

	def isAbstract = is(O.ACC_ABSTRACT)
	def isAnnotation = is(O.ACC_ANNOTATION)
	def isBridge = is(O.ACC_BRIDGE)
	def isDeprecated = is(O.ACC_DEPRECATED)
	def isEnum = is(O.ACC_ENUM)
	def isFinal = is(O.ACC_FINAL)
	def isInterface = is(O.ACC_INTERFACE)
	def isMandated = is(O.ACC_MANDATED)
	def isNative = is(O.ACC_NATIVE)
	def isPrivate = is(O.ACC_PRIVATE)
	def isProtected = is(O.ACC_PROTECTED)
	def isPublic = is(O.ACC_PUBLIC)
	def isStatic = is(O.ACC_STATIC)
	def isStrict = is(O.ACC_STRICT)
	def isSuper = is(O.ACC_SUPER)
	def isSynchronized = is(O.ACC_SYNCHRONIZED)
	def isSynthetic = is(O.ACC_SYNTHETIC)
	def isTransient = is(O.ACC_TRANSIENT)
	def isVarargs = is(O.ACC_VARARGS)
	def isVolatile = is(O.ACC_VOLATILE)

	private def stringifyWith(f: (String => Unit) => Unit) = {
		val sb = new StringBuilder
		var addedFirst = false
		def add(s: String): Unit = {
			if(addedFirst) sb += ' '
			else addedFirst = true
			sb append s
		}
		f(add _)
		sb.result()
	}

	def toStringForClass = stringifyWith { add =>
		if(isPublic) add("public")
		if(isPrivate) add("private")
		if(isProtected) add("protected")

		if(isFinal) add("final")
		if(isStatic) add("static")
		if(isAbstract) add("abstract")

		if(isAnnotation) add("@interface")
		else if(isEnum) add("enum")
		else if(isInterface) add("interface")
		else add("class")
	}

	def toStringForMethod = stringifyWith { add =>
		if(isPublic) add("public")
		if(isPrivate) add("private")
		if(isProtected) add("protected")

		if(isFinal) add("final")
		if(isStatic) add("static")
		if(isAbstract) add("abstract")
		if(isNative) add("native")
		if(isSynchronized) add("synchronized")
	}

}

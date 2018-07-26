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

/** Represents a class's name.
  *
  * This class exists basically to give a bit of a type-level hint as to
  * whether the name is being expressed with "dot notation" (i.e. "java.lang.String")
  * or "slash notation" (i.e. "java/lang/String").
  *
  * Instances can only be created via the companion object's `fromDotted`
  * and `fromSlashed` methods.
  */
final case class ClassName private[ClassName](slashedName: String) extends AnyVal {
	def dottedName = slashedName.replace('/', '.')
	override def toString = dottedName
}

object ClassName {
	def fromDotted(dottedName: String) = new ClassName(dottedName.replace('.', '/'))
	def fromSlashed(slashedName: String) = new ClassName(slashedName)
}
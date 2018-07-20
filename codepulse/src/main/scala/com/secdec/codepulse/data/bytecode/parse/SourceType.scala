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

import com.github.javaparser.ast.`type`.Type
import com.github.javaparser.ast.body.Parameter

/*
This file contains an assortment of "model" classes used to represent information about Java source files.
 */

/** Represents the three primary java "types" that can be declared */
sealed abstract class TypeKind(val alias: String)
object TypeKind {
	case object ClassKind extends TypeKind("class")
	case object InterfaceKind extends TypeKind("interface")
	case object EnumKind extends TypeKind("enum")

	def fromAlias(alias: String): TypeKind = alias match {
		case "class" => ClassKind
		case "interface" => InterfaceKind
		case "enum" => EnumKind
		case _ => throw new IllegalArgumentException(s"Invalid TypeKind alias: $alias")
	}
}

case class SourceTypeReference(ref: String) extends AnyVal
object SourceTypeReference {
	def apply(`type`: Type): SourceTypeReference = apply(`type`.toString)//toStringWithoutComments)
	def apply(param: Parameter): SourceTypeReference = {
		val s = param.getName.toString//param.getType.toStringWithoutComments
		val extra = if (param.isVarArgs) "[]" else ""
		apply(s + extra)
	}
}
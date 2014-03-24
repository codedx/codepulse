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

/** Describes a Class type, as part of a MethodSignature.
  * A Method's parameter list and return type are both comprised
  * of MethodTypeParams.
  */
sealed trait MethodTypeParam

object MethodTypeParam {

	/** A primitive JVM type such as "Byte", "Int", or "Long" */
	case class Primitive(name: String) extends MethodTypeParam {
		override def toString = name.toLowerCase
	}

	/** A reference type such as "java.lang.String" */
	case class ReferenceType(name: String) extends MethodTypeParam {
		override def toString = name
	}

	/** An array type, representing an Array of `innerType` */
	case class ArrayType(innerType: MethodTypeParam) extends MethodTypeParam {
		override def toString = innerType.toString + "[]"
	}

	def fuzzyEquals(left: MethodTypeParam, right: MethodTypeParam): Boolean = (left, right) match {
		case (Primitive(l), Primitive(r)) => l == r
		case (ArrayType(l), ArrayType(r)) => fuzzyEquals(l, r)
		case (ReferenceType(l), ReferenceType(r)) => (l endsWith r) || (r endsWith l)
		case _ => false
	}
}
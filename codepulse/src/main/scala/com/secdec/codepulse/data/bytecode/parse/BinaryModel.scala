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

import org.objectweb.asm.signature.SignatureVisitor

/** Represents a type in the JVM. Used for things like specifying the type of a method argument,
  * the super-class of a class, and more.
  *
  * There are four main "kinds" of type signature:
  *  - BaseType (for primitives like 'int' and 'char'
  *  - TypeVariable (for generics e.g. the 'A' in 'List<A>'
  *  - ArrayType (for arrays of other types)
  *  - ClassType (for arbitrary classes, possibly with generics)
  */
sealed trait TypeSignature
object TypeSignature {
	case class BaseType(descriptor: Char) extends TypeSignature {
		override def toString = BaseType.namesMap.getOrElse(descriptor, descriptor.toString)
	}
	object BaseType {
		val ofByte = BaseType('B')
		val ofChar = BaseType('C')
		val ofDouble = BaseType('D')
		val ofFloat = BaseType('F')
		val ofInt = BaseType('I')
		val ofLong = BaseType('J')
		val ofShort = BaseType('S')
		val ofBoolean = BaseType('Z')
		val ofVoid = BaseType('V')

		val byDescriptor = Map(
			'B' -> ofByte,
			'C' -> ofChar,
			'D' -> ofDouble,
			'F' -> ofFloat,
			'I' -> ofInt,
			'J' -> ofLong,
			'S' -> ofShort,
			'Z' -> ofBoolean,
			'V' -> ofVoid
		)

		val namesMap = Map(
			'B' -> "byte",
			'C' -> "char",
			'D' -> "double",
			'F' -> "float",
			'I' -> "int",
			'J' -> "long",
			'S' -> "short",
			'Z' -> "boolean",
			'V' -> "void"
		)
	}

	case class TypeVariable(name: String) extends TypeSignature {
		override def toString = name
	}
	case class ArrayType(elementType: TypeSignature) extends TypeSignature {
		override def toString = s"$elementType[]"
	}
	case class ClassType(name: ClassName, typeArguments: List[TypeArgument], innerClass: Option[ClassType]) extends TypeSignature {
		override def toString = {
			val sb = new StringBuilder
			sb append name
			if(typeArguments.nonEmpty) sb append typeArguments.mkString("<", ", ", ">")
			for(ic <- innerClass) sb append s".$ic"
			sb.result()
		}

		def flatten: List[(ClassName, List[TypeArgument])] = {
			(name, typeArguments) :: innerClass.map(_.flatten).getOrElse(Nil)
		}
	}

	def fuzzyEquals(left: TypeSignature, right: TypeSignature): Boolean = (left, right) match {
		case (BaseType(l), BaseType(r)) => l == r
		case (TypeVariable(l), TypeVariable(r)) => l == r
		case (ArrayType(l), ArrayType(r)) => fuzzyEquals(l, r)
		case (ClassType(lName, _, lInner), ClassType(rName, _, rInner)) =>
			// we'll ignore the type arguments, but make sure the name chains are fuzzily-equal
			val ls = lName.slashedName
			val rs = rName.slashedName
			val namesMatch = (ls endsWith rs) || (rs endsWith ls)
			if(namesMatch){
				(lInner, rInner) match {
					case (Some(li), Some(ri)) => fuzzyEquals(li, ri)
					case (None, None) => true
					case _ => false
				}
			} else {
				false
			}
		case _ => false
	}
}

/** Represents a generic argument within a TypeSignature
  */
sealed trait TypeArgument
object TypeArgument {
	// stands for a <?> type
	case object Unbounded extends TypeArgument

	// stands for a <N> type where N is known, and may possibly be a <? extends N> or <? super N>
	case class Bounded(variance: TypeVariance, typeSig: TypeSignature) extends TypeArgument {
		import TypeVariance._
		override def toString = variance match {
			case InstanceOf => typeSig.toString
			case Super => s"super $typeSig"
			case Extends => s"extends $typeSig"
		}
	}

}

/** Represents the variance of a type argument, e.g. the difference between
  * `Foo<String>` and `Foo<? extends String>` and `Foo<? super String>`.
  * @param descriptor The character used by ASM to indicate a particular variance.
  *                   Possible values are '-', '+', and '='
  */
final case class TypeVariance private(descriptor: Char) extends AnyVal
object TypeVariance {
	val InstanceOf = TypeVariance(SignatureVisitor.INSTANCEOF)
	val Super = TypeVariance(SignatureVisitor.SUPER)
	val Extends = TypeVariance(SignatureVisitor.EXTENDS)

	def fromChar(char: Char) = char match {
		case '-' => Super
		case '+' => Extends
		case _ => InstanceOf
	}
}

/** Represents a generic type parameter specified by a method or a class.
  * For example, in
  * {{{ class Foo<A extends Bar> }}}
  * There would be a `FormalTypeParameter("A", Some(ClassType("Bar"), Nil)`
  *
  * @param name
  * @param classBound
  * @param interfaceBounds
  */
case class FormalTypeParameter(name: String, classBound: Option[TypeSignature], interfaceBounds: List[TypeSignature]) {
	override def toString = {
		val allBounds = classBound match {
			case None => interfaceBounds
			case Some(cb) => cb :: interfaceBounds
		}
		val boundsStr = if(allBounds.nonEmpty) allBounds.mkString(" extends ", " & ", "") else ""
		name + boundsStr
	}
}

/** Represents all of the types involved with a class's signature
  *
  * @param typeParams
  * @param superClass
  * @param interfaces
  */
case class JVMClassSignature(typeParams: List[FormalTypeParameter], superClass: TypeSignature, interfaces: List[TypeSignature])

/** Represents all of the types involved with a method's signature
  *
  * @param typeParams
  * @param paramTypes
  * @param returnType
  * @param exceptionTypes
  */
case class JVMMethodTypeSignature(typeParams: List[FormalTypeParameter], paramTypes: List[TypeSignature], returnType: TypeSignature, exceptionTypes: List[TypeSignature])

/** Size information about a piece of code (usually a method).
  *
  * @param instructionCount The number of bytecode instructions in the method's implementation.
  *                         Note that this may be 0 for abstract/interface methods.
  * @param lineCount The number of lines of code in the method's implementation, if any line-level
  *                  debug information was found in the bytecode.
  */
case class CodeSize(instructionCount: Int, lineCount: Option[Int])
object CodeSize {
	implicit val codeSizeOrdering: Ordering[CodeSize] = Ordering.by { s => (s.instructionCount, s.lineCount) }
}

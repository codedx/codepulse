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

import org.objectweb.asm._
import org.objectweb.asm.signature._

import scala.annotation.tailrec
import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.{NoPosition, Position, Reader}

/** Typeclass for converting various ASM/JVM "Signature" types to and from String.
  *
  * For any given `T` for which there is a `SignatureConverter[T]`, you can call
  * `SignatureConverter.fromString[T](rawSig)` and `SignatureConverter.toString[T](sig)`.
  */
trait JVMSignatureConverter[T] {
	def fromString(sig: String): T
	def toString(sig: T): String
}

/** Parsing based off ASM SignatureVisitor's methods.
  * See http://asm.ow2.org/asm50/javadoc/user/org/objectweb/asm/signature/SignatureVisitor.html for details.
  * There are three top-level signature kinds that the SignatureVisitor is expected to handle (detailed at the link above):
  * ClassSignature, MethodSignature, and TypeSignature
  * Created by DylanH on 4/27/2016.
  */
object JVMSignatureConverter {

	def fromString[T: JVMSignatureConverter](sig: String) = {
		implicitly[JVMSignatureConverter[T]].fromString(sig)
	}
	def toString[T: JVMSignatureConverter](sig: T) = {
		implicitly[JVMSignatureConverter[T]].toString(sig)
	}

	// SignatureConverter[BinaryMethodSignature]
	implicit object JVMMethodSignatureConverter extends BaseJVMSignatureConverter[JVMMethodTypeSignature](
		{ _ accept _ }, SigParsers.methodSignatureParser, visitMethodSignature
	)

	// SignatureConverter[BinaryClassSignature]
	implicit object JVMClassSignatureConverter extends BaseJVMSignatureConverter[JVMClassSignature](
		{ _ accept _ }, SigParsers.classSignatureParser, visitClassSignature
	)

	// SignatureConverter[TypeSignature]
	implicit object JVMTypeSignatureConverter extends BaseJVMSignatureConverter[TypeSignature](
		{ _ acceptType _ }, SigParsers.typeSignatureParser, visitTypeSignature
	)


	abstract class BaseJVMSignatureConverter[T](
		onParse: (SignatureReader, SignatureVisitor) => Unit,
		parser: SigParsers.Parser[T],
		write: (T, SignatureVisitor) => Unit
	) extends JVMSignatureConverter[T] {
		def fromString(sig: String): T = {
			val visitor = new SigPartCollectingVisitor
			val reader = new SignatureReader(sig)
			onParse(reader, visitor)
			val parts = visitor.result()
			SigParsers.parseOrElseThrow(parser, parts)
		}
		def toString(sig: T): String = {
			val writer = new SignatureWriter
			write(sig, writer)
			writer.toString
		}
	}

	/** Representation of steps taken by a SignatureVisitor.
	  * These are used internally by the `SigParsers` in order
	  * to generate actual Signature instances.
	  */
	sealed trait SigPart
	object SigPart {
		case object VisitArrayType extends SigPart
		case class VisitBaseType(descriptor: Char) extends SigPart
		case object VisitClassBound extends SigPart
		case class VisitClassType(name: String) extends SigPart
		case object VisitEnd extends SigPart
		case object VisitExceptionType extends SigPart
		case class VisitFormalTypeParameter(name: String) extends SigPart
		case class VisitInnerClassType(name: String) extends SigPart
		case object VisitInterface extends SigPart
		case object VisitInterfaceBound extends SigPart
		case object VisitParameterType extends SigPart
		case object VisitReturnType extends SigPart
		case object VisitSuperclass extends SigPart
		case object VisitUnboundedTypeArgument extends SigPart
		case class VisitTypeArgument(wildcard: Char) extends SigPart
		case class VisitTypeVariable(name: String) extends SigPart
	}

	/** ASM SignatureVisitor that accumulates all of the "visitX" instructions
	  * to a list of `SigPart`s, which can be retrieved by calling `result()`.
	  */
	class SigPartCollectingVisitor extends SignatureVisitor(Opcodes.ASM7) {
		private val parts = List.newBuilder[SigPart]
		private def add(part: SigPart): this.type = {
			parts += part
			this
		}
		def result() = parts.result()

		import SigPart._
		override def visitFormalTypeParameter(s: String): Unit = add(VisitFormalTypeParameter(s))
		override def visitClassType(s: String): Unit = add(VisitClassType(s))
		override def visitExceptionType(): SignatureVisitor = add(VisitExceptionType)
		override def visitInnerClassType(s: String): Unit = add(VisitInnerClassType(s))
		override def visitBaseType(c: Char): Unit = add(VisitBaseType(c))
		override def visitArrayType(): SignatureVisitor = add(VisitArrayType)
		override def visitInterface(): SignatureVisitor = add(VisitInterface)
		override def visitParameterType(): SignatureVisitor = add(VisitParameterType)
		override def visitEnd(): Unit = add(VisitEnd)
		override def visitInterfaceBound(): SignatureVisitor = add(VisitInterfaceBound)
		override def visitReturnType(): SignatureVisitor = add(VisitReturnType)
		override def visitClassBound(): SignatureVisitor = add(VisitClassBound)
		override def visitTypeVariable(s: String): Unit = add(VisitTypeVariable(s))
		override def visitSuperclass(): SignatureVisitor = add(VisitSuperclass)
		override def visitTypeArgument(): Unit = add(VisitUnboundedTypeArgument)
		override def visitTypeArgument(c: Char): SignatureVisitor = add(VisitTypeArgument(c))
	}

	object SigParsers extends Parsers {
		type Elem = SigPart
		import SigPart._

		def tryParse[T](p: Parser[T], sigParts: List[SigPart]) = p(ListReader(sigParts)) match {
			case Success(result, _) => scala.util.Success(result)
			case NoSuccess(msg, _) => scala.util.Failure(new Exception(s"Parse failed: $msg"))
		}

		def parseOrElseThrow[T](p: Parser[T], sigParts: List[SigPart]) = p(ListReader(sigParts)) match {
			case Success(result, _) => result
			case NoSuccess(msg, _) =>
				println(s"Failed to parse: $sigParts")
				throw new Exception(s"Parse failed: $msg")
		}

		case class ListReader(elems: List[Elem]) extends Reader[Elem] {
			def first: Elem = elems.head
			def atEnd: Boolean = elems.isEmpty
			def pos: Position = NoPosition
			def rest: Reader[Elem] = ListReader(if(atEnd) Nil else elems.tail)
		}

		lazy val typeSignatureParser: Parser[TypeSignature] = {
			import TypeSignature._
			val baseTypeParser = acceptMatch("VisitBaseType", {
				case VisitBaseType(t) => BaseType.byDescriptor.getOrElse(t, BaseType(t))
			})
			val typeVariableParser = acceptMatch("VisitTypeVariable", { case VisitTypeVariable(name) => TypeVariable(name) })
			val arrayTypeParser = (VisitArrayType ~> typeSignatureParser map { ArrayType(_) })
			val classTypeParser = {
				// described as:
				// ( visitClassType visitTypeArgument* ( visitInnerClassType visitTypeArgument* )* visitEnd ) )
				// and we need to define `visitTypeArgument` and `visitInnerClassType` internally
				val typeArgumentParser = {
					val boundedParser = acceptMatch("VisitTypeArgument", { case VisitTypeArgument(wildcard) => wildcard }) ~ typeSignatureParser map {
						case wildcard ~ sig => TypeArgument.Bounded(TypeVariance fromChar wildcard, sig)
					}
					val unboundedParser = accept(VisitUnboundedTypeArgument) ^^^ TypeArgument.Unbounded
					boundedParser | unboundedParser
				}
				val innerClassTypeParser = {
					acceptMatch("VisitInnerClassType", { case VisitInnerClassType(name) => name }) ~ rep(typeArgumentParser) map {
						case name ~ typeArgs => ClassType(ClassName fromSlashed name, typeArgs, None)
					}
				}
				acceptMatch("VisitClassType", { case VisitClassType(name) => name }) ~ rep(typeArgumentParser) ~ rep(innerClassTypeParser) <~ VisitEnd map {
					case name ~ typeArgs ~ innerClassChain =>
						val innerClass = innerClassChain.foldRight(Option.empty[ClassType]){ (outer, inner) =>
							Some(outer.copy(innerClass = inner))
						}
						ClassType(ClassName fromSlashed name, typeArgs, innerClass)
				}
			}

			baseTypeParser | typeVariableParser | arrayTypeParser | classTypeParser
		}

		lazy val formalTypeParameterParser: Parser[FormalTypeParameter] = {
			acceptMatch("VisitFormalTypeParameter", { case VisitFormalTypeParameter(name) => name }) ~
				(VisitClassBound ~> typeSignatureParser).? ~
				rep(VisitInterfaceBound ~> typeSignatureParser) map {
				case name ~ classBound ~ interfaceBounds => FormalTypeParameter(name, classBound, interfaceBounds)
			}
		}

		lazy val classSignatureParser: Parser[JVMClassSignature] = {
			rep(formalTypeParameterParser) ~
				(VisitSuperclass ~> typeSignatureParser) ~
				rep(VisitInterface ~> typeSignatureParser) map {
				case tParams ~ superClass ~ interfaces =>
					JVMClassSignature(tParams, superClass, interfaces)
			}
		}

		lazy val methodSignatureParser: Parser[JVMMethodTypeSignature] = {
			rep(formalTypeParameterParser) ~
				rep(VisitParameterType ~> typeSignatureParser) ~
				(VisitReturnType ~> typeSignatureParser) ~
				rep(VisitExceptionType ~> typeSignatureParser) map {
				case tParams ~ paramTypes ~ returnType ~ exceptionTypes =>
					JVMMethodTypeSignature(tParams, paramTypes, returnType, exceptionTypes)
			}
		}
	}

	def visitTypeArg(typeArgument: TypeArgument, visitor: SignatureVisitor): Unit = typeArgument match {
		case TypeArgument.Bounded(variance, typeSig) =>
			val innerVisitor = visitor.visitTypeArgument(variance.descriptor)
			visitTypeSignature(typeSig, innerVisitor)
		case TypeArgument.Unbounded =>
			visitor.visitTypeArgument()
	}

	@tailrec def visitInnerClassChain(innerClass: Option[TypeSignature.ClassType], visitor: SignatureVisitor): Unit = innerClass match {
		case None => // done
		case Some(ic) =>
			visitor.visitInnerClassType(ic.name.slashedName)
			ic.typeArguments.foreach(visitTypeArg(_, visitor))
			visitInnerClassChain(ic.innerClass, visitor)
	}

	def visitTypeSignature(sig: TypeSignature, visitor: SignatureVisitor): Unit = sig match {
		case TypeSignature.BaseType(descriptor) =>
			visitor.visitBaseType(descriptor)
		case TypeSignature.ArrayType(innerType) =>
			val innerVisitor = visitor.visitArrayType()
			visitTypeSignature(innerType, innerVisitor)
		case TypeSignature.TypeVariable(name) =>
			visitor.visitTypeVariable(name)
		case TypeSignature.ClassType(name, typeArgs, innerClass) =>
			visitor.visitClassType(name.slashedName)
			typeArgs.foreach(visitTypeArg(_, visitor))
			visitInnerClassChain(innerClass, visitor)
			visitor.visitEnd()
	}

	def visitFormalTypeParameter(tParam: FormalTypeParameter, visitor: SignatureVisitor) = {
		visitor.visitFormalTypeParameter(tParam.name)
		for(classBound <- tParam.classBound){
			visitor.visitClassBound()
			visitTypeSignature(classBound, visitor)
		}
		for(interfaceBound <- tParam.interfaceBounds){
			visitor.visitInterfaceBound()
			visitTypeSignature(interfaceBound, visitor)
		}
	}

	def visitClassSignature(sig: JVMClassSignature, visitor: SignatureVisitor): Unit = {
		// formal type params
		sig.typeParams.foreach(visitFormalTypeParameter(_, visitor))

		// superclass
		val superVisitor = visitor.visitSuperclass()
		visitTypeSignature(sig.superClass, superVisitor)

		// interfaces
		for(superInterface <- sig.interfaces){
			val interfaceVisitor = visitor.visitInterface()
			visitTypeSignature(superInterface, interfaceVisitor)
		}
	}

	def visitMethodSignature(sig: JVMMethodTypeSignature, visitor: SignatureVisitor): Unit = {
		// formal type params
		sig.typeParams.foreach(visitFormalTypeParameter(_, visitor))

		// arguments
		for(pType <- sig.paramTypes) {
			val pVisitor = visitor.visitParameterType()
			visitTypeSignature(pType, pVisitor)
		}

		// return type
		val rVisitor = visitor.visitReturnType()
		visitTypeSignature(sig.returnType, rVisitor)

		// exceptions
		for(eType <- sig.exceptionTypes){
			val eVisitor = visitor.visitExceptionType()
			visitTypeSignature(eType, eVisitor)
		}
	}
}

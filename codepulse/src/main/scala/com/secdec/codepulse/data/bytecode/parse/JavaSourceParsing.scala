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

import java.io.{File, InputStream}

//import com.avi.codedx.data.model.{ClassName, LocationRange}
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body._
import com.github.javaparser.ast.{CompilationUnit, Node}
import com.github.javaparser.Position
import org.apache.commons.io.FileUtils

import scala.collection.JavaConversions._
import scala.util.Try

case class LocationRange(start: Position, end: Position)

object JavaSourceParsing {

	case class ClassInfo(
		signature: JavaSourceClassSignature,
		lines: LocationRange,
		memberMethods: List[MethodInfo],
		memberClasses: List[ClassInfo]
	)

	case class MethodInfo(
		signature: JavaSourceMethodSignature,
		lines: LocationRange
	)

//	// TODO: this is just here for testing, so remove it later
//	def loadAllJavaFilesIn(dir: File) = {
//		val allFiles = FileUtils.listFiles(dir, Array("java"), true).toVector
//		val allCompilationUnits = allFiles.map(JavaParser.parse)
//		val typesByFile = allCompilationUnits.map(getClassesFrom)
//		allFiles.zip(typesByFile)
//	}

	/** Main method for getting structural information on java source files */
	def tryParseJavaSource(src: InputStream): Try[List[ClassInfo]] = Try {
		val cu = JavaParser.parse(src)
		getClassesFrom(cu)
	}

	// ======================================== //
	// Everything below this is helper methods! //
	// ======================================== //

	/** Convert a java list (which might be null) to a scala list */
	def scalaList[T](javaList: java.util.List[T]) = Option(javaList).map(_.toList).getOrElse(Nil)

	/** Get a LocationRange representing the lines of the given AST node */
	def lineRangeOf(node: Node) = LocationRange(node.getBegin().get, node.getEnd().get)

	/** Convert a `javaparser` representation of a method declaration to our internal model */
	def getMethodDeclaration(md: MethodDeclaration): MethodInfo = {
		val name = md.getName.toString
		val modifiers = AccessFlags(enumSetToLong(md.getModifiers).toInt)
		val returnType = SourceTypeReference(md.getType)
		val argumentTypes = scalaList(md.getParameters).map(SourceTypeReference(_))
		val lines = LocationRange(md.getBegin().get, md.getEnd().get)
		val sig = JavaSourceMethodSignature(modifiers, returnType, name, argumentTypes)
		MethodInfo(sig, lines)
	}

	/** We will represent constructors in a manner consistent with the bytecode representation,
	  * e.g. a `void <init>` function, with the usual logic for modifiers and parameters
	  */
	def getConstructorDeclaration(cd: ConstructorDeclaration): MethodInfo = {
		val modifiers = AccessFlags(enumSetToLong(cd.getModifiers).toInt)
		val argumentTypes = scalaList(cd.getParameters).map(SourceTypeReference(_))
		val lines = LocationRange(cd.getBegin().get, cd.getEnd().get)
		val sig = JavaSourceMethodSignature(modifiers, SourceTypeReference("void"), "<init>", argumentTypes)
		MethodInfo(sig, lines)
	}

	/** Convert a `javaparser` representation of an enum declaration to our internal model */
	def getClassDeclaration(makeName: String => ClassName, ed: EnumDeclaration): (JavaSourceClassSignature, LocationRange) = {
		val name = makeName(ed.getName.toString)
		val modifiers = AccessFlags(enumSetToLong(ed.getModifiers).toInt)
		val kind = TypeKind.EnumKind
		val implTypes = scalaList(ed.getImplementedTypes).map(SourceTypeReference(_))
		val lines = lineRangeOf(ed)
		val sig = JavaSourceClassSignature(modifiers, kind, name, tParams = Nil, ext = Nil, impl = implTypes)
		sig -> lines
	}

	/** Convert a `javaparser` representation of a class/interface declaration to our internal model */
	def getClassDeclaration(makeName: String => ClassName, cd: ClassOrInterfaceDeclaration): (JavaSourceClassSignature, LocationRange) = {
		val name = makeName(cd.getName.toString)
		val modifiers = AccessFlags(enumSetToLong(cd.getModifiers).toInt)
		val kind = if(cd.isInterface) TypeKind.InterfaceKind else TypeKind.ClassKind
		val tParams = scalaList(cd.getTypeParameters).map(_.getName.toString)
		val extendedTypes = scalaList(cd.getExtendedTypes).map(SourceTypeReference(_))
		val implementedTypes = scalaList(cd.getImplementedTypes).map(SourceTypeReference(_))
		val sig = JavaSourceClassSignature(modifiers, kind, name, tParams, extendedTypes, implementedTypes)
		val lines = lineRangeOf(cd)
		sig -> lines
	}

	/** Convert a `javaparser` representation of a class/enum/interface declaration, along with all of its
	  * member methods and inner types, to our internal model. Recurses into inner types to build a tree.
	  */
	def getClassStructure(makeName: String => ClassName, t: Either[EnumDeclaration, ClassOrInterfaceDeclaration]): ClassInfo = {
		val (classSig, classLines) = t match {
			case Left(ed) => getClassDeclaration(makeName, ed)
			case Right(cd) => getClassDeclaration(makeName, cd)
		}
		val rawMembers = scalaList(t.merge.getMembers).asInstanceOf[List[BodyDeclaration[_ <: BodyDeclaration[_]]]]
		// locate methods belonging to `t`
		val methods = rawMembers collect {
			case md: MethodDeclaration => getMethodDeclaration(md)
			case cd: ConstructorDeclaration => getConstructorDeclaration(cd)
		}
		// construct a function that makes names for the inner classes/enums found inside `t`
		val makeInnerName = { name: String =>
			val prefix = classSig.name.slashedName
			val dollarSep = name.replaceAllLiterally(".", "$")
			ClassName.fromSlashed(prefix + '$' + dollarSep)
		}
		// locate and handle any inner classes/enums
		val classes = rawMembers collect {
			case ed: EnumDeclaration => getClassStructure(makeInnerName, Left(ed))
			case cd: ClassOrInterfaceDeclaration => getClassStructure(makeInnerName, Right(cd))
		}
		ClassInfo(classSig, classLines, methods, classes)
	}

	/** Pull all of the type declarations (note that java can declare multiple top-level types in a file,
	  * but only one can be public), and store their structure as instances of our internal model of things.
	  */
	def getClassesFrom(cu: CompilationUnit): List[ClassInfo] = {
		// package name may be null - want slash-separated - and tack on another slash if it's not empty
		val pkgPrefix = Option(cu.getPackageDeclaration.get).map(_.getName + "/").getOrElse("").replaceAllLiterally(".", "/")
		val makeName = { name: String => ClassName.fromSlashed(pkgPrefix + name) }
		// Compiler barfs on the constraints and erasure of constrained type information
		// By using an explicit cast, we bring it back
		val x = scalaList(cu.getTypes).asInstanceOf[List[TypeDeclaration[_ <: TypeDeclaration[_]]]]
		val z: PartialFunction[TypeDeclaration[_ <: TypeDeclaration[_]], ClassInfo] = {
			case ed: EnumDeclaration => getClassStructure(makeName, Left(ed))
			case cd: ClassOrInterfaceDeclaration => getClassStructure(makeName, Right(cd))
		}
		x collect z
	}

	import java.util
	def enumSetToLong[T <: Enum[T]](set: util.EnumSet[T]): Long = {
		var r: Long = 0
		import scala.collection.JavaConversions._
		for (value <- set) {
			r |= 1L << value.ordinal
		}
		r
	}
}

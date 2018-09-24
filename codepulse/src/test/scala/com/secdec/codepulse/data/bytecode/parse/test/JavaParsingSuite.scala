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
package com.secdec.codepulse.data.bytecode.parse.test

import java.io.FileInputStream
import scala.util.{ Failure, Success, Try }

import com.secdec.codepulse.data.bytecode.AsmVisitors
import com.secdec.codepulse.data.bytecode.parse.{ JavaBinaryMethodSignature, JavaSourceParsing, MethodSignature }
import org.scalatest.FunSpec
import org.scalatest._
import org.scalatest.Matchers._

class JavaParsingSuite extends FunSpec with Matchers {
	val SOURCE = getClass.getResource("Main.java").getPath.replaceAll("%20", " ")
	val BINARY = getClass.getResource("Main.class").getPath.replaceAll("%20", " ")

	val MAIN_SIGNATURE = "com/sample/Main.main;void;String[]"

	def signatureAsString(signature: MethodSignature): String = {
		signature.name + ";" + signature.simplifiedReturnType.name + ";" + signature.simplifiedArgumentTypes.map(_.name).mkString(";")
	}

	describe("Java interpretation") {
		it("should recognize source method information") {
			// get stream to source
			val stream = new FileInputStream(SOURCE)

			// use java parsing on stream to build hierarchy
			val source = JavaSourceParsing.tryParseJavaSource(stream)

			// get desired method signature as a string
			val clazz = source.map(hierarchy => hierarchy.head)
			val method = source.map(hierarchy => hierarchy.head.memberMethods.head)
			val methodAsSignature = for {
				className <- clazz.map(c => c.signature.name.slashedName)
				methodSignature <- method.map(m => signatureAsString(m.signature))
			} yield { className + "." + methodSignature }

			// match to canned result expectation
			val matches = methodAsSignature.filter(sig => sig == MAIN_SIGNATURE) match {
				case Success(sig) => true
				case Failure(_) => false
			}

			matches should equal(true)
		}

		it("should recognize binary method information") {
			// get stream to binary
			val stream = new FileInputStream(BINARY)

			// use asm on stream to get method signature as string
			val methods = AsmVisitors.parseMethodsFromClass(stream)
			val (_, name, _, _) = methods.last

			val Array(nameStr: String, accessStr: String, rawSignature: String) = name.split(";", 3)
			val binaryMethodSignature = Try(JavaBinaryMethodSignature.parseMemoV1(String.join(";", accessStr, nameStr, rawSignature)))
			val methodAsSignature = binaryMethodSignature.map(sig => signatureAsString(sig))

			// match to canned result expectation
			val matches = methodAsSignature.filter(sig => sig == MAIN_SIGNATURE) match {
				case Success(sig) => true
				case Failure(_) => false
			}

			matches should equal(true)
		}

		it("should generate equivalent data for source and binary method information") {
			// get source method signature
			// get stream to source
			val sourceStream = new FileInputStream(SOURCE)

			// use java parsing on stream to build hierarchy
			val source = JavaSourceParsing.tryParseJavaSource(sourceStream)

			// get desired method signature as a string
			val sourceClass = source.map(hierarchy => hierarchy.head)
			val sourceMethod = source.map(hierarchy => hierarchy.head.memberMethods.head)
			val sourceMethodAsSignature = for {
				className <- sourceClass.map(c => c.signature.name.slashedName)
				methodSignature <- sourceMethod.map(m => signatureAsString(m.signature))
			} yield { className + "." + methodSignature }

			// get binary method signature
			// get stream to binary
			val binaryStream = new FileInputStream(BINARY)

			// use asm on stream to get method signature as string
			val binaryMethods = AsmVisitors.parseMethodsFromClass(binaryStream)
			val (_, binaryName, _, _) = binaryMethods.last

			val Array(binaryNameStr: String, binaryAccessStr: String, binaryRawSignature: String) = binaryName.split(";", 3)
			val binaryMethodSignature = Try(JavaBinaryMethodSignature.parseMemoV1(String.join(";", binaryAccessStr, binaryNameStr, binaryRawSignature)))
			val binaryMethodAsSignature = binaryMethodSignature.map(sig => signatureAsString(sig))

			// match signatures
			val matches = (for {
				sourceSignature <- sourceMethodAsSignature
				binarySignature <- binaryMethodAsSignature
			} yield {
				sourceSignature == binarySignature
			}).get

			matches should equal(true)
		}
	}
}

/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.secdec.bytefrog.agent.bytefrog.test

import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSuite

import com.secdec.bytefrog.agent.bytefrog.test.cases._
import com.secdec.bytefrog.agent.bytefrog.test.util._
import com.secdec.bytefrog.agent.bytefrog.test.util.TestScriptEntry._
import com.secdec.bytefrog.agent.util.MockHelpers

import com.codedx.bytefrog.instrumentation.id._

class InstrumentationSuite extends FunSuite with MockFactory with MockHelpers {
	val classIdentifier = new ClassIdentifier
	val methodIdentifier = new MethodIdentifier
	implicit val runner = new TestRunner(classIdentifier, methodIdentifier)

	test("Simple Method Instrumentation") {
		TestScript[SimpleTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main", false),
		MethodVisit(1, 29))
		/*
		 * ID 0 assigned to <init> ()V 25:25 (compiler-provided, default ctor)
		 */
			.run()
	}

	test("Static Initializer Instrumentation") {
		TestScript[StaticInitializerTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>", false),
		MethodVisit(4, 28),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main", false),
		MethodVisit(3, 32))
		/*
		 * ID 2 assigned to <init> ()V 25:25 (compiler-provided, default ctor)
		 */
			.run()
	}

	test("Simple Constructor Instrumentation") {
		TestScript[SimpleConstructorTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>", false),
		MethodVisit(6, 33),
		MethodVisit(6, 34),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main", false),
		MethodVisit(5, 29),
		MethodVisit(5, 30))
			.run()
	}

	test("Super Constructor Instrumentation") {
		TestScript[SuperConstructorTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>", false),
		MethodVisit(10, 40),
		MethodVisit(10, 41),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>", false),
		MethodVisit(9, 47),
		MethodVisit(9, 48),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main", false),
		MethodVisit(8, 34),
		MethodVisit(8, 35))
		/*
		 * ID 7 assigned to <init> ()V 30:30 (compiler-provided, default ctor)
		 */
			.run()
	}

	test("Layered Super Constructor Instrumentation") {
		TestScript[MultipleSuperConstructorTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>", false),
		MethodVisit(16, 41),
		MethodVisit(16, 42),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>", false),
		MethodVisit(15, 48),
		MethodVisit(15, 49),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>", false),
		MethodVisit(14, 55),
		MethodVisit(14, 56),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>", false),
		MethodVisit(13, 62),
		MethodVisit(13, 63),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main", false),
		MethodVisit(12, 35),
		MethodVisit(12, 36))
		/*
		 * ID 11 assigned to <init> ()V 31:31 (compiler-provided, default ctor)
		 */
			.run()
	}

	test("Exception throw/catch without bubble") {
		TestScript[ExceptionThrowTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main", false),
		MethodVisit(18, 33),
		MethodVisit(18, 35),
		MethodVisit(18, 38))
		/*
		 * ID 17 assigned to <init> ()V 27:27 (compiler-provided, default ctor)
		 */
			.run()
	}

	test("Exception throw/bubble with catch") {
		TestScript[ExceptionBubbleTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower", true),
		MethodVisit(21, 42),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main", false),
		MethodVisit(20, 33),
		MethodVisit(20, 35),
		MethodVisit(20, 38))
		/*
		* ID 19 assigned to <init> ()V 27:27 (compiler-provided, default ctor)
		*/
			.run()
	}

	test("Exception throw/finally/bubble with catch") {
		TestScript[ExceptionFinallyBubbleTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower", true),
		MethodVisit(24, 47),
		MethodVisit(24, 51),
		MethodVisit(24, 52),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main", false),
		MethodVisit(23, 35),
		MethodVisit(23, 37),
		MethodVisit(23, 40))
		/*
		* ID 22 assigned to <init> ()V 29:29 (compiler-provided, default ctor)
		*/
			.run()
	}

	test("Exception throw/bubble by constructor with catch") {
		TestScript[ConstructorThrowTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>", true),
		MethodVisit(26, 41),
		MethodVisit(26, 42),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main", false),
		MethodVisit(25, 33),
		MethodVisit(25, 35),
		MethodVisit(25, 38))
			.run()
	}

	test("Exception throw/bubble by super constructor with catch") {
		TestScript[SuperConstructorThrowTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>", true),
		MethodVisit(30, 49),
		MethodVisit(30, 50),
		/*
		 * ID 29 assigned to <init> ()V 57:58 (compiler-provided, default ctor)
		 */
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main", false),
		MethodVisit(28, 39),
		MethodVisit(28, 41),
		MethodVisit(28, 44))
		/*
		 * ID 27 assigned to <init> ()V 33:33 (compiler-provided, default ctor)
		 */
			.run()
	}
}
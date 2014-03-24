/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
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

package com.secdec.bytefrog.agent.bytefrog.test

import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSuite

import com.secdec.bytefrog.agent.bytefrog.test.cases._
import com.secdec.bytefrog.agent.bytefrog.test.util._
import com.secdec.bytefrog.agent.bytefrog.test.util.TestScriptEntry._
import com.secdec.bytefrog.agent.util.MockHelpers

class InstrumentationSuite extends FunSuite with MockFactory with MockHelpers {
	implicit val runner = new TestRunner

	test("Simple Method Instrumentation") {
		TestScript[SimpleTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main"))
			.run()
	}

	test("Static Initializer Instrumentation") {
		TestScript[StaticInitializerTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main"))
			.run()
	}

	test("Simple Constructor Instrumentation") {
		TestScript[SimpleConstructorTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main"))
			.run()
	}

	test("Super Constructor Instrumentation") {
		TestScript[SuperConstructorTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main"))
			.run()
	}

	test("Layered Super Constructor Instrumentation") {
		TestScript[MultipleSuperConstructorTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main"))
			.run()
	}

	test("Exception throw/catch without bubble") {
		TestScript[ExceptionThrowTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main"),
			Exception("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main", "java.io.IOException"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main"))
			.run()
	}

	test("Exception throw/bubble with catch") {
		TestScript[ExceptionBubbleTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower"),
			Exception("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower", "java.io.IOException"),
			ExceptionBubble("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower", "java.io.IOException"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main"))
			.run()
	}

	test("Exception throw/finally/bubble with catch") {
		TestScript[ExceptionFinallyBubbleTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower"),
			Exception("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower", "java.io.IOException"),
			Exception("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower", "java.io.IOException"),
			ExceptionBubble("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower", "java.io.IOException"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main"))
			.run()
	}

	test("Exception throw/bubble by constructor with catch") {
		TestScript[ConstructorThrowTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>"),
			Exception("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>", "java.io.IOException"),
			ExceptionBubble("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>", "java.io.IOException"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main"))
			.run()
	}

	test("Exception throw/bubble by super constructor with catch") {
		TestScript[SuperConstructorThrowTest](
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>"),
			Exception("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>", "java.io.IOException"),
			ExceptionBubble("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>", "java.io.IOException"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main"))
			.run()
	}
}
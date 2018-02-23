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

class InstrumentationSuite extends FunSuite with MockFactory with MockHelpers {
	implicit val runner = new TestRunner

	val nextIdLock = new Object
	var nextId = 0
	private def id(): Int = nextIdLock.synchronized {
		nextId += 1
		nextId
	}

	val identifiers = List(
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main",
		"com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>"
	) map (signature => signature -> id) toMap

	test("Simple Method Instrumentation") {
		TestScript[SimpleTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main")))
			.run()
	}

	test("Static Initializer Instrumentation") {
		TestScript[StaticInitializerTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main")))
			.run()
	}

	test("Simple Constructor Instrumentation") {
		TestScript[SimpleConstructorTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main")))
			.run()
	}

	test("Super Constructor Instrumentation") {
		TestScript[SuperConstructorTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main")))
			.run()
	}

	test("Layered Super Constructor Instrumentation") {
		TestScript[MultipleSuperConstructorTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>")),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main")))
			.run()
	}

	test("Exception throw/catch without bubble") {
		TestScript[ExceptionThrowTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main")),
			Exception(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main"), "java.io.IOException"),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main")))
			.run()
	}

	test("Exception throw/bubble with catch") {
		TestScript[ExceptionBubbleTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower")),
			Exception(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower"), "java.io.IOException"),
			ExceptionBubble(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower"), "java.io.IOException"),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main")))
			.run()
	}

	test("Exception throw/finally/bubble with catch") {
		TestScript[ExceptionFinallyBubbleTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower")),
			Exception(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower"), "java.io.IOException"),
			Exception(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower"), "java.io.IOException"),
			ExceptionBubble(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower"), "java.io.IOException"),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main")))
			.run()
	}

	test("Exception throw/bubble by constructor with catch") {
		TestScript[ConstructorThrowTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>")),
			Exception(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>"), "java.io.IOException"),
			ExceptionBubble(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>"), "java.io.IOException"),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main")))
			.run()
	}

	test("Exception throw/bubble by super constructor with catch") {
		TestScript[SuperConstructorThrowTest](
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main")),
			MethodEntry(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>")),
			Exception(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>"), "java.io.IOException"),
			ExceptionBubble(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>"), "java.io.IOException"),
			MethodExit(identifiers("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main")))
			.run()
	}
}
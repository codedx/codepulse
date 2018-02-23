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
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleTest.main", false))
			.run()
	}

	test("Static Initializer Instrumentation") {
		TestScript[StaticInitializerTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.<clinit>", false),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.StaticInitializerTest.main", false))
			.run()
	}

	test("Simple Constructor Instrumentation") {
		TestScript[SimpleConstructorTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.<init>", false),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SimpleConstructorTest.main", false))
			.run()
	}

	test("Super Constructor Instrumentation") {
		TestScript[SuperConstructorTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$SuperClass.<init>", false),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest$ChildClass.<init>", false),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorTest.main", false))
			.run()
	}

	test("Layered Super Constructor Instrumentation") {
		TestScript[MultipleSuperConstructorTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass1.<init>", false),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass2.<init>", false),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$SuperClass3.<init>", false),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest$ChildClass.<init>", false),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.MultipleSuperConstructorTest.main", false))
			.run()
	}

	test("Exception throw/catch without bubble") {
		TestScript[ExceptionThrowTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionThrowTest.main", false))
			.run()
	}

	test("Exception throw/bubble with catch") {
		TestScript[ExceptionBubbleTest](classIdentifier, methodIdentifier,
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main"),
			MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower"),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.thrower", true),
			MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionBubbleTest.main", false))
			.run()
	}

	test("Exception throw/finally/bubble with catch") {
		TestScript[ExceptionFinallyBubbleTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.thrower", true),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ExceptionFinallyBubbleTest.main", false))
			.run()
	}

	test("Exception throw/bubble by constructor with catch") {
		TestScript[ConstructorThrowTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.<init>", true),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.ConstructorThrowTest.main", false))
			.run()
	}

	test("Exception throw/bubble by super constructor with catch") {
		TestScript[SuperConstructorThrowTest](classIdentifier, methodIdentifier,
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main"),
		MethodEntry("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>"),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest$SuperThrower.<init>", true),
		MethodExit("com.secdec.bytefrog.agent.bytefrog.test.cases.SuperConstructorThrowTest.main", false))
			.run()
	}
}
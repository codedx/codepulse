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

package com.secdec.bytefrog.agent.bytefrog.test.util

import scala.collection.mutable.ListBuffer
import scala.collection.mutable

import org.scalatest.exceptions.TestFailedException
import com.codedx.codepulse.agent.trace.TraceDataCollector

import com.codedx.bytefrog.instrumentation.id._

sealed trait TestScriptEntry
object TestScriptEntry {
	case class MethodEntry(method: String) extends TestScriptEntry
	case class MethodExit(method: String, exThrown: Boolean) extends TestScriptEntry
}

object TestScript {
	def apply[T](classIdentifier: ClassIdentifier, methodIdentifier: MethodIdentifier, script: TestScriptEntry*)(implicit runner: TestRunner, m: Manifest[T]) = new TestScript[T](classIdentifier, methodIdentifier, script)
}

class TraceDataCollectorImpl(data: ListBuffer[TestScriptEntry], classIdentifier: ClassIdentifier, methodIdentifier: MethodIdentifier) extends TraceDataCollector {
	def methodEntry(method: Int) {
		val mi = methodIdentifier.get(method)
		val ci = classIdentifier.get(mi.getClassId)
		data += TestScriptEntry.MethodEntry(s"${ci.getName}.${mi.getName}")
	}

	def methodExit(method: Int, exThrown: Boolean) {
		val mi = methodIdentifier.get(method)
		val ci = classIdentifier.get(mi.getClassId)
		data += TestScriptEntry.MethodExit(s"${ci.getName}.${mi.getName}", exThrown)
	}

	def recordLineLevelTrace(methodId: Int, startLine: Int, endLine: Int, lineMap: java.util.BitSet): Unit = {
	}
}

/** A helper class that defines a bytefrog test script, i.e., expected results
  * TODO: add thread safey/support for threaded test cases (will probably require changes to TestRunner too)
  *
  * @author robertf
  */
class TestScript[T](classIdentifier: ClassIdentifier, methodIdentifier: MethodIdentifier, script: Seq[TestScriptEntry])(implicit runner: TestRunner, m: Manifest[T]) {
	def isCorrect = observed.corresponds(expected) { _ == _ }

	def expected = script
	def observed = data.toSeq

	import TestScriptEntry._

	private val data = ListBuffer[TestScriptEntry]()

	private def trimMethod(sig: String) = sig.takeWhile(_ != ';')

	private implicit val dataCollector = new TraceDataCollectorImpl(data, classIdentifier, methodIdentifier)

	def run(arguments: java.lang.String*) {
		try {
			runner.runTest[T](arguments: _*)
		} catch {
			case e: Throwable => e.printStackTrace() // ignore
		} finally {
			if (!isCorrect)
				throw new TestFailedException(
					s"Test did follow the script.\n\ngot:\n${observed.mkString("\t", "\n\t", "")}\n\nexpected:\n${expected.mkString("\t", "\n\t", "")}",
					0)
		}
	}
}
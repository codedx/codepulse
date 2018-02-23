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

sealed trait TestScriptEntry
object TestScriptEntry {
	case class MethodEntry(method: Int) extends TestScriptEntry
	case class MethodExit(method: Int) extends TestScriptEntry
	case class Exception(method: Int, exception: String) extends TestScriptEntry
	case class ExceptionBubble(method: Int, exception: String) extends TestScriptEntry
	case class Marker(key: String, value: String) extends TestScriptEntry
}

object TestScript {
	def apply[T](script: TestScriptEntry*)(implicit runner: TestRunner, m: Manifest[T]) = new TestScript[T](script)
}

class TraceDataCollectorImpl(data: ListBuffer[TestScriptEntry], trimMethod: String => String) extends TraceDataCollector {
	val nextIdLock = new Object
	var nextId = 0
	private def id(): Int = nextIdLock.synchronized {
		nextId += 1
		nextId
	}

	private var identifiers = mutable.Map[String, Int]()
	private def insert(signature: String): Int = identifiers.getOrElseUpdate(trimMethod(signature), id)

	def methodEntry(method: Int) {
		data += TestScriptEntry.MethodEntry(method)
	}

	def methodExit(method: Int, line: Int) {
		data += TestScriptEntry.MethodExit(method)
	}

	def exception(exception: String, method: Int, line: Int) {
		data += TestScriptEntry.Exception(method, exception)
	}

	def bubbleException(exception: String, method: Int) {
		data += TestScriptEntry.ExceptionBubble(method, exception)
	}

	def marker(key: String, value: String) {
		data += TestScriptEntry.Marker(key, value)
	}
}

/** A helper class that defines a bytefrog test script, i.e., expected results
  * TODO: add thread safey/support for threaded test cases (will probably require changes to TestRunner too)
  *
  * @author robertf
  */
class TestScript[T](script: Seq[TestScriptEntry])(implicit runner: TestRunner, m: Manifest[T]) {
	def isCorrect = observed.corresponds(expected) { _ == _ }

	def expected = script
	def observed = data.toSeq

	import TestScriptEntry._

	private val data = ListBuffer[TestScriptEntry]()

	private def trimMethod(sig: String) = sig.takeWhile(_ != ';')

	private implicit val dataCollector = new TraceDataCollectorImpl(data, trimMethod)

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
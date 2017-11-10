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

import java.lang.reflect.InvocationTargetException

import com.secdec.bytefrog.agent.TraceDataCollector
import com.secdec.bytefrog.agent.trace.Trace

/** A helper class that will load and instrument a test class with bytefrog and run the "main" method on it
  * with the given trace data collector.
  *
  * @author robertf
  */
class TestRunner {
	private val instrumentor = new TestInstrumentor

	def runTest[T](arguments: java.lang.String*)(implicit dataCollector: TraceDataCollector, m: Manifest[T]) {
		try {
			Trace setTraceDataCollector dataCollector

			val c = instrumentor.getInstrumentedClass[T]

			val main = c.getMethod("main", classOf[Array[java.lang.String]])

			main.invoke(null, arguments.toArray)
		} catch {
			case e: InvocationTargetException =>
				throw e.getCause
		} finally {
			Trace setTraceDataCollector null
		}
	}
}
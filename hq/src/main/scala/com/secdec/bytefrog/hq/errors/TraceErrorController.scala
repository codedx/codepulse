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

package com.codedx.codepulse.hq.errors

import reactive.EventSource
import reactive.EventStream

/** Exposes a means of reporting and listening for errors within the Application,
  * and within a single, unspecified Trace.
  */
class TraceErrorController {

	// private access to the "fire" method for errors
	private val traceErrorSource = new EventSource[Error]
	/** Exposes a read-only stream of Error events triggered by `reportTraceError` */
	def traceErrors: EventStream[Error] = traceErrorSource

	// private access to the "fire" method for warnings
	private val traceWarningSource = new EventSource[Error]
	/** Exposes a read-only stream of Error events triggered by `reportTraceWarning` */
	def traceWarnings: EventStream[Error] = traceWarningSource

	private var traceShuttingDown = false
	/** Is the Trace shutting down? This matters because if it *is* shutting down,
	  * a ConditionalError is treated as non-fatal.
	  */
	def isTraceShuttingDown = traceShuttingDown

	/** Signal this controller that the Trace is shutting down. This operation is irreversable.
	  */
	def setTraceShuttingDown = { traceShuttingDown = true }

	/** Report an application-wide error. This method delegates to `ApplicationErrorController`. */
	def reportApplicationError(err: Error) = ApplicationErrorController.reportApplicationError(err)

	/** Report an application-wide warning. This method delegates to `ApplicationErrorController`. */
	def reportApplicationWarning(err: Error) = ApplicationErrorController.reportApplicationWarning(err)

	def reportTraceError(err: Error) = traceErrorSource.fire(err)
	def reportTraceWarning(err: Error) = traceWarningSource.fire(err)

	/** Exposes a read-only stream of *fatal* Trace errors. Conditional errors
	  * are not treated as fatal when the trace is shutting down, as indicated
	  * by `isTraceShuttingDown`.
	  */
	val fatalTraceErrors = traceErrors.filter {
		case UnexpectedError(_, _) => true
		case ConditionalError(_, _) => !isTraceShuttingDown
	}

	/** Exposes a read-only stream of *fatal* errors from both the current Trace and
	  * the Application. To get a stream of fatal errors that only originate from the
	  * Trace, use `fatalTraceErrors`.
	  */
	val fatalErrors = fatalTraceErrors | ApplicationErrorController.applicationErrors

}
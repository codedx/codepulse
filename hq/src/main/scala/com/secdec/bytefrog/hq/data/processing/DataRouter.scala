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

package com.codedx.codepulse.hq.data.processing

import scala.collection.mutable.ArrayBuffer

import com.codedx.codepulse.hq.errors.TraceErrorController
import com.codedx.codepulse.hq.errors.UnexpectedError
import com.codedx.codepulse.hq.protocol.DataMessageContent
import com.codedx.codepulse.hq.trace.Cleanup
import com.codedx.codepulse.hq.util.CompletionHooks

/** DataRouter is responsible for receiving all data as it comes off the DataCollector and relaying it
  * back out to whatever DataProcessors are registered.
  *
  * Normal lifetime will trickle down to us from the data connections, and be propagated via postStop.
  * In the case of an abnormal halt, `Trace` should kill us AND any data processors.
  *
  * @author robertf
  */
class DataRouter(traceErrorController: TraceErrorController) extends CompletionHooks with Cleanup {
	private val processors = ArrayBuffer.empty[DataProcessor]

	private var _messagesRouted = 0L
	def messagesRouted = _messagesRouted

	def +=(p: DataProcessor) = processors += p
	def ++=(p: TraversableOnce[DataProcessor]) = processors ++= p

	def -=(p: DataProcessor) = processors -= p
	def --=(p: TraversableOnce[DataProcessor]) = processors --= p

	def route(message: DataMessageContent) = try {
		processors.foreach(_.processMessage(message))
		_messagesRouted += 1
	} catch {
		case t: Throwable => traceErrorController.reportTraceError(UnexpectedError("Error processing trace data", Some(t)))
	}

	def routeDataBreak() = processors.foreach(_.processDataBreak)

	def finish = try {
		println("Finishing processors...")
		for (p <- processors) println(s"  to finish: $p")
		processors.foreach(_.finishProcessing)
		complete
	} catch {
		case t: Throwable => traceErrorController.reportTraceError(UnexpectedError("Error processing trace data", Some(t)))
	}

	def cleanup = processors.foreach(_.cleanup)
}
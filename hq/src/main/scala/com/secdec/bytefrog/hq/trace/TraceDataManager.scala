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

package com.secdec.bytefrog.hq.trace

import com.secdec.bytefrog.hq.data.processing.DataRouter

/** Indirectly tells the trace where to put its data. Segments go through
  * a different code path than regular data, so there are two setup methods.
  * Implementing classes can attach DataProcessors to a DataRouter via the
  * `setupDataProcessors` method. Handlers for trace segments can be set up
  * via the `setupSegmentProcessing` method. Cleanup/save code can be run
  * in the finish method.
  */
trait TraceDataManager {
	def setupDataProcessors(router: DataRouter): Unit
	def setupSegmentProcessing(startTime: Long): TraceSegmentManager
	def finish(reason: TraceEndReason, traceWasStarted: Boolean): Unit
}
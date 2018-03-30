/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
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

package com.secdec.codepulse.tracer

import com.codedx.codepulse.hq.data.processing.DataRouter
import com.codedx.codepulse.hq.trace.DefaultSegmentAccess
import com.codedx.codepulse.hq.trace.TraceDataManager
import com.codedx.codepulse.hq.trace.TraceEndReason
import com.codedx.codepulse.hq.trace.TraceSegmentManager
import com.secdec.codepulse.data.jsp.JspMapper
import com.secdec.codepulse.data.model.ProjectData

/** A TraceDataManager that records events through the `TraceRecorderServer`,
  * which will make the events available through a REST api for use by the
  * javascript client.
  *
  * Currently, methods must be correlated between binary and source IDs; this is
  * done at constructor time by creating a "PrecalculatedMethodCorrelator" for the
  * given analysis.
  */
class StreamingTraceDataManager(projectData: ProjectData, transientData: TransientTraceData, jspMapper: Option[JspMapper]) extends TraceDataManager {

	/** Set up listeners for the various trace segment builder events,
	  * adapt them to LiveSegmentData instances,
	  * and fire them back through the `liveSegmentData` event stream.
	  */
	def setupSegmentProcessing(startTime: Long) = {
		new TraceSegmentManager(new DefaultSegmentAccess)
	}

	/** Set up a processor that gathers time-bucketed trace data and
	  * fires it back through the `liveTraceData` event stream.
	  */
	def setupDataProcessors(router: DataRouter): Unit = {
		router += new TraceRecorderDataProcessor(projectData, transientData, jspMapper)
	}

	def finish(reason: TraceEndReason, traceWasStarted: Boolean): Unit = {
		val t = if (traceWasStarted) "Trace" else "An unstarted trace"
		println(s"$t stopped because $reason")
	}
}
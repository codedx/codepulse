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

package com.codedx.codepulse.hq.trace

import com.codedx.codepulse.hq.data.TraceSegmentEvent

import reactive.EventSource
import reactive.EventStream

trait HasTraceSegmentBuilder {
	private var _sm: TraceSegmentManager = _
	private val es = new EventSource[TraceSegmentEvent]

	protected def segmentManager_=(sm: TraceSegmentManager) = {
		_sm = sm.wrap { sa =>
			new SegmentAccessNotifier(sa, { es fire _ })
		}
	}
	protected def segmentManager = _sm

	/** Tell the segmentManager to open a segment with the given name
	  * with the current time as the timestamp.
	  * @return The ID of the segment that gets opened
	  */
	def openSegment(name: String): Int = {
		segmentManager.openSegment(name, System.currentTimeMillis)
	}

	/** Tell the segmentManager to close the latest segment, using
	  * the current time as the timestamp.
	  */
	def closeSegment() = {
		segmentManager.close(System.currentTimeMillis)
	}

	/** Tell the segmentManager to rename the given segment.
	  *
	  * @param id The ID of the segment to rename
	  * @param newName The new name for the segment
	  */
	def renameSegment(id: Int, newName: String) = {
		segmentManager.renameSegment(id, newName)
	}

	/** Exposes generated segment events through an EventStream */
	def segmentEvents: EventStream[TraceSegmentEvent] = es

}
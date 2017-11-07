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

import java.util.concurrent.atomic.AtomicInteger

import com.secdec.bytefrog.hq.data.TraceSegmentEvent

/** Defines an interface for saving segments.
  */
trait SegmentAccess {
	/** Create a new segment and return its ID */
	def openSegmentRecord(name: String, startTime: Long, parentId: Option[Int]): Int

	/** Create a new anonymous segment and return its ID */
	def openAnonSegmentRecord(startTime: Long, parentId: Option[Int]): Int

	/** Close a segment by its ID */
	def closeSegmentRecord(id: Int, endTime: Long): Unit

	/** Rename a segment by its ID */
	def renameSegmentRecord(id: Int, newName: String): Unit

	/** Delete a segment by its ID */
	def deleteSegmentRecord(id: Int): Unit
}

/** A basic SegmentAccess that simply returns atomically-incrementing
  * ID values for each `open` call. Everything else is no-ops.
  */
class DefaultSegmentAccess extends SegmentAccess {
	private val idAtom = new AtomicInteger(0)

	def openSegmentRecord(name: String, startTime: Long, parentId: Option[Int]): Int = idAtom.getAndIncrement
	def openAnonSegmentRecord(startTime: Long, parentId: Option[Int]): Int = idAtom.getAndIncrement
	def closeSegmentRecord(id: Int, endTime: Long): Unit = ()
	def renameSegmentRecord(id: Int, newName: String): Unit = ()
	def deleteSegmentRecord(id: Int): Unit = ()
}

/** A SegmentAccess that delegates actual functionality to a `proxy` object, then
  * creates a `TraceSegmentEvent` for each method call and sends it to the
  * `eventsCallback`.
  *
  * @param proxy The SegmentAccess to delegate actual functionality
  * @param eventsCallback A handler for the generated events
  */
class SegmentAccessNotifier(proxy: SegmentAccess, eventsCallback: TraceSegmentEvent => Unit) extends SegmentAccess {
	import TraceSegmentEvent._

	def openSegmentRecord(name: String, startTime: Long, parentId: Option[Int]): Int = {
		val id = proxy.openSegmentRecord(name, startTime, parentId)
		eventsCallback { SegmentOpened(id, name, startTime, parentId) }
		id
	}

	def openAnonSegmentRecord(startTime: Long, parentId: Option[Int]): Int = {
		val id = proxy.openAnonSegmentRecord(startTime, parentId)
		eventsCallback { AnonSegmentOpened(id, startTime, parentId) }
		id
	}

	def closeSegmentRecord(id: Int, endTime: Long): Unit = {
		proxy.closeSegmentRecord(id, endTime)
		eventsCallback { SegmentClosed(id, endTime) }
	}

	def renameSegmentRecord(id: Int, newName: String): Unit = {
		proxy.renameSegmentRecord(id, newName)
		eventsCallback { SegmentRenamed(id, newName) }
	}

	def deleteSegmentRecord(id: Int): Unit = {
		proxy.deleteSegmentRecord(id)
		eventsCallback { SegmentDeleted(id) }
	}
}

/** A SegmentAccess that delegates functionality to a `proxy` object, after applying
  * a `timeOffset` to all start and end times passed as arguments.
  *
  * @param proxy The SegmentAccess that work is delegated to
  * @param timeOffset A function that transforms timestamps before passing them to the proxy
  */
class TimeOffsetSegmentAccess(proxy: SegmentAccess, timeOffset: Long => Long) extends SegmentAccess {
	def openSegmentRecord(name: String, startTime: Long, parentId: Option[Int]): Int =
		proxy.openSegmentRecord(name, timeOffset(startTime), parentId)

	def openAnonSegmentRecord(startTime: Long, parentId: Option[Int]): Int =
		proxy.openAnonSegmentRecord(timeOffset(startTime), parentId)

	def closeSegmentRecord(id: Int, endTime: Long): Unit =
		proxy.closeSegmentRecord(id, timeOffset(endTime))

	def renameSegmentRecord(id: Int, newName: String): Unit =
		proxy.renameSegmentRecord(id, newName)

	def deleteSegmentRecord(id: Int): Unit =
		proxy.deleteSegmentRecord(id)
}
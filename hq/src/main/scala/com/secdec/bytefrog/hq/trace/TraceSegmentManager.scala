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

package com.codedx.codepulse.hq.trace

import scala.annotation.tailrec

/** An object that exposes an API for opening, closing, and renaming
  * Trace Segments, but additionally generates anonymous segments
  * that fit between the explicitly-created segments.
  *
  * @param segmentAccess An object whose methods this Manager will
  * call for all of its internal activity, including anonymous segments.
  */
class TraceSegmentManager(segmentAccess: SegmentAccess) {
	type ID = Int
	type Time = Long

	private val segments = collection.mutable.Map[ID, Segment]()
	private val rootSegment = new Segment(-1, "<root>", 0, None)
	private var current = rootSegment

	private var completionHooks = List[() => Unit]()

	/** Creates a new TraceSegmentManager that uses a modified version
	  * of this one's segmentAccess.
	  *
	  * @param f A function that generates a new SegmentAccess instance
	  * given this manager's SegmentAccess
	  * @return A TraceSegmentManager that uses the SegmentAccess generated
	  * by the `f` function.
	  */
	def wrap(f: SegmentAccess => SegmentAccess): TraceSegmentManager = {
		val access2 = f(segmentAccess)
		val tsm = new TraceSegmentManager(access2)

		// also transfer over any registered completionHooks
		for (hook <- completionHooks) tsm.onComplete { hook() }
		tsm
	}

	/** Register a function to run when this manager "completes"
	  */
	def onComplete(thunk: => Unit) = {
		completionHooks ::= { () => thunk }
		this
	}

	/** Complete this manager by closing any un-closed segments,
	  * then running any completionHooks that were registered
	  * via `onComplete`.
	  */
	def complete(time: Time): Unit = {
		if (close(time)) {
			complete(time)
		} else {
			for (thunk <- completionHooks.reverseIterator) thunk()
		}
	}

	def openSegment(name: String, time: Time): ID = {
		val seg = createSegment(name, time, parent = Some(current))

		// close whatever the latest autoChild was
		for (auto <- current.autoChildren.headOption) auto.closeTime = time

		// add the new segment as a real child of the current one
		current.realChildren ::= seg

		current = seg

		// create an auto-generated segment as a child of the new segment
		val auto = createAnonSegment(time, parent = Some(seg))
		seg.autoChildren ::= auto

		auto.id
	}

	def renameSegment(id: ID, newName: String) = {
		for (seg <- segments.get(id)) seg.name = newName
	}

	def close(time: Time): Boolean = {
		if (current != rootSegment) {
			// if the current segment doesn't have real children, delete the auto-children
			if (current.realChildren.isEmpty) {
				for (a <- current.autoChildren) a.deleteMe
				current.autoChildren = Nil
			} else {
				// close the latest auto child
				for (a <- current.autoChildren.headOption) {
					a.closeTime = time
				}
			}

			// set the right end time
			current.closeTime = time

			// step back up the stack
			// open an <anon> segment in the parent
			for (parent <- current.parent) {
				current = parent
				val auto = createAnonSegment(time, Some(parent))
				parent.autoChildren ::= auto
			}

			true
		} else {
			false
		}
	}

	private def fixParentId(parent: Option[Segment]): Option[ID] = parent flatMap {
		case seg if seg.id < 0 => None
		case seg => Some(seg.id)
	}

	private def createSegment(name: String, openTime: Time, parent: Option[Segment]): Segment = {
		val id = segmentAccess.openSegmentRecord(name, openTime, fixParentId(parent))
		new Segment(id, name, openTime, parent)
	}

	private def createAnonSegment(openTime: Time, parent: Option[Segment]): Segment = {
		val id = segmentAccess.openAnonSegmentRecord(openTime, fixParentId(parent))
		new Segment(id, "<anon>", openTime, parent)
	}

	/** Internal representation of trace segments. Has mutable fields `closeTime` and `name`
	  * which will trigger changes via the `segmentAccess` when they are changed. Includes a
	  * `deleteMe` method to trigger a deletion via `segmentAccess`. Can keep track of
	  * children using the `realChildren` and `autoChildren` lists.
	  */
	private class Segment(val id: ID, private var _name: String, val openTime: Time, val parent: Option[Segment]) {
		segments(id) = this

		private var _closeTime = openTime
		def closeTime = _closeTime
		def closeTime_=(time: Time) = {
			segmentAccess.closeSegmentRecord(id, time)
			_closeTime = time
		}

		def name = _name
		def name_=(newName: String) = {
			segmentAccess.renameSegmentRecord(id, newName)
			_name = newName
		}

		def deleteMe: Unit = {
			for (c <- realChildren) c.deleteMe
			for (c <- autoChildren) c.deleteMe
			segmentAccess.deleteSegmentRecord(id)
			segments.remove(id)
		}
		var realChildren = List[Segment]()
		var autoChildren = List[Segment]()
	}

}
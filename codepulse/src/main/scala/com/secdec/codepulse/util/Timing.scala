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

package com.secdec.codepulse.util

import net.liftweb.common.Loggable
import net.liftweb.util.TimeHelpers

object Timing extends Loggable {

	/** Abstracts the concept of time and duration to type parameters T and D respectively.
	  */
	trait TimeProvider[T, D] {

		/** Get the current time */
		def now: T

		/** Get a duration based on the difference between a start and end time */
		def duration(start: T, end: T): D

		/** Generate a string to represent the duration, generally adding a unit suffix */
		def formatDuration(dur: D): String
	}

	/** A time provider that gets the system time as milliseconds */
	object SystemTime extends TimeProvider[Long, Long] {
		def now = System.currentTimeMillis
		def duration(start: Long, end: Long) = end - start
		def formatDuration(dur: Long) = dur + " ms"
	}

	/** A time provider that gets the system's "nano" time as nanoseconds */
	object SystemNanoTime extends TimeProvider[Long, Long] {
		def now = System.nanoTime
		def duration(start: Long, end: Long) = end - start
		def formatDuration(dur: Long) = dur + " ns"
	}

	/** Helper: calculates the time taken to perform the `task`, using the given TimeProvider */
	private def innerCalcTime[D, T, R](t: TimeProvider[T, D], task: => R): (D, R) = {
		val start = t.now
		val result = task
		val end = t.now
		t.duration(start, end) -> result
	}

	/** Helper: logs a message, reporting how long it took, according to `time` and `t` */
	private def logTimeMessage[D](msg: String, time: D, t: TimeProvider[_, D], log: (=> AnyRef) => Unit) = log(s"$msg took ${t.formatDuration(time)}")

	/** Helper: calculates the time taken to perform `task`, then logs it via `logTimeMessage` */
	private def logCalcTime[D, R](msg: String, t: TimeProvider[_, D], log: (=> AnyRef) => Unit)(task: => R): R = {
		val (d, r) = innerCalcTime(t, task)
		logTimeMessage(msg, d, t, log)
		r
	}

	def logTime[T](label: String)(task: => T): T = logCalcTime(label, SystemTime, logger.info)(task)
	def logNanoTime[T](label: String)(task: => T): T = logCalcTime(label, SystemNanoTime, logger.info)(task)
	def debugTime[T](label: String)(task: => T): T = logCalcTime(label, SystemTime, logger.debug)(task)
	def debugNanoTime[T](label: String)(task: => T): T = logCalcTime(label, SystemNanoTime, logger.debug)(task)

	def calcTime[T](task: => T) = innerCalcTime(SystemTime, task)
	def calcNanoTime[T](task: => T) = innerCalcTime(SystemNanoTime, task)

	def logTime(label: String, time: Long) = logTimeMessage(label, time, SystemTime, logger.info)
	def debugTime(label: String, time: Long) = logTimeMessage(label, time, SystemTime, logger.debug)
	def logNanoTime(label: String, time: Long) = logTimeMessage(label, time, SystemNanoTime, logger.info)
	def debugNanoTime(label: String, time: Long) = logTimeMessage(label, time, SystemNanoTime, logger.debug)

	/** Mix this trait into any class to add the `accumTiming`, `clearTimings`,
	  * and `printTimings` variety methods to that class. The target class must
	  * provide a `timingLabel`, which will be prepended to timing messages
	  * generated for printing.
	  */
	trait Accumulator {

		/** This label will be prepended to messages about timings */
		protected def timingLabel: String

		private val timings = collection.mutable.Map[String, Long]().withDefaultValue(0)

		/** Perform the given `job`, and add the time it took to run to the given `label`. */
		def accumTiming[T](label: String)(job: => T) = {
			val (nanos, result) = calcNanoTime { job }
			timings(label) += nanos
			result
		}

		/** Clear the currently-accumulated times stored by `accumTiming` */
		def clearTimings = timings.clear

		/** Print messages about the currently-accumulated times stored by
		  * `accumTiming`. This method accepts a `log` function which should
		  * print the given message along with the total time (milliseconds)
		  * associated with each label.
		  */
		def printTimings(log: (String, Long) => Unit) = {
			for { (label, nanos) <- timings.toList.sortBy(_._1) }
				log(s"$timingLabel: $label", nanos / 1000000)
		}

		/** `printTimings`, with `Timing.logTime` as the printer */
		def logTimings = printTimings(logTime)

		/** `printTimings`, with `Timing.debugTime` as the printer */
		def debugTimings = printTimings(debugTime)

	}

	/** Run the given `body` of code, passing in a temporary `Accumulator`
	  * instance that uses the given `prefix` as its `timingLabel`.
	  */
	def withTimeAccumulator[T](prefix: String)(body: Accumulator => T): T = {
		val acc = new Accumulator { val timingLabel = prefix }
		body(acc)
	}

}
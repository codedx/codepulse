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

package com.codedx.codepulse.hq.monitor

import reactive.EventSource
import reactive.EventStream

/** Describes the health status of a TraceComponent. Instances of this class will generally be
  * created by HealthMonitor implementations, and eventually checked by the UI.
  */
case class TraceComponentHealth(component: TraceComponent, status: TraceComponentStatus, message: Option[String] = None, data: Option[TraceComponentMonitorData] = None)

/** Base for trace component monitor data */
trait TraceComponentMonitorData

/** A runnable task that checks the health of a trace component.
  *
  * Monitors are Runnables, with the expectation that they will be used by a
  * ScheduledExecutorService, scheduled to repeat every once in a while.
  */
trait HealthMonitor extends Runnable {
	/** Run interval for monitor, in milliseconds.
	  */
	val runInterval: Int = 5000
	def checkHealth: TraceComponentHealth

	/** Event source that gets triggered when this monitor is run
	  * and reports a new health status.
	  */
	private val healthUpdatesSource = new EventSource[TraceComponentHealth]()
	def healthUpdates: EventStream[TraceComponentHealth] = healthUpdatesSource

	def run = {
		val health = checkHealth

		healthUpdatesSource.fire(health)
	}

	/** Convenience method that creates a "healthy" status for the
	  * implicitly-provided `cmp` TraceComponent.
	  */
	def healthy(implicit cmp: TraceComponent) =
		TraceComponentHealth(cmp, TraceComponentStatus.Healthy)

	/** Convenience method that creates a "healthy" status for the
	  * implicitly-provided `cmp` TraceComponent with the given `data`.
	  */
	def healthy(data: TraceComponentMonitorData)(implicit cmp: TraceComponent) =
		TraceComponentHealth(cmp, TraceComponentStatus.Healthy, data = Some(data))

	/** Convenience method that creates a "concerned" status with the
	  * given `msg` for the implicitly-provided `cmp` TraceComponent.
	  */
	def concerned(msg: String)(implicit cmp: TraceComponent) =
		TraceComponentHealth(cmp, TraceComponentStatus.Concerned, message = Some(msg))

	/** Convenience method that creates a "concerned" status with the
	  * given `msg` and `data` for the implicitly-provided `cmp` TraceComponent.
	  */
	def concerned(msg: String, data: TraceComponentMonitorData)(implicit cmp: TraceComponent) =
		TraceComponentHealth(cmp, TraceComponentStatus.Concerned, message = Some(msg), data = Some(data))

	/** Convenience method that creates an "unhealthy" status with the
	  * given `msg` for the implicitly-provided `cmp` TraceComponent.
	  */
	def unhealthy(msg: String)(implicit cmp: TraceComponent) =
		TraceComponentHealth(cmp, TraceComponentStatus.Unhealthy, message = Some(msg))

	/** Convenience method that creates an "unhealthy" status with the
	  * given `msg` and `data` for the implicitly-provided `cmp` TraceComponent.
	  */
	def unhealthy(msg: String, data: TraceComponentMonitorData)(implicit cmp: TraceComponent) =
		TraceComponentHealth(cmp, TraceComponentStatus.Unhealthy, message = Some(msg), data = Some(data))
}
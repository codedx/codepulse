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

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

import scala.collection.mutable.HashMap

import com.codedx.codepulse.hq.monitor.HealthMonitor
import com.codedx.codepulse.hq.monitor.TraceComponent
import com.codedx.codepulse.hq.monitor.TraceComponentHealth
import com.codedx.codepulse.hq.monitor.TraceComponentMonitorData
import com.codedx.codepulse.hq.util.DaemonThreadFactory

import reactive.EventSource
import reactive.EventStream
import reactive.Observing

/** An aggregator and scheduler for a collection of HealthMonitors.
  * Individual monitors may be added and removed from this collection;
  * each monitor will be run at its decided rate in a thread pool, and
  * the status which it reports will be added to an internal status map
  * that records the most up-to-date status for each TraceComponent.
  *
  * @param threadPoolSize The number of threads to use in the internal
  * executor. Defaults to 1.
  */
class TraceStatus(threadPoolSize: Integer = 1) extends Cleanup with Observing {
	private val statusMap = new HashMap[TraceComponent, TraceComponentHealth]
	private val executor = new ScheduledThreadPoolExecutor(threadPoolSize, DaemonThreadFactory)

	/** An Event that gets triggered when one of the connected HealthMonitors
	  * reports a new status for its associated component.
	  */
	def healthChanges: EventStream[TraceComponentHealth] = healthChangeSource
	private val healthChangeSource = new EventSource[TraceComponentHealth]

	/** An event that gets triggered when new monitor data is reported */
	def monitorData: EventStream[TraceComponentMonitorData] = healthChangeSource.map { _.data }.filter { _.isDefined }.map { _.get }

	private def reportHealth(health: TraceComponentHealth) {
		statusMap.synchronized { statusMap.put(health.component, health) } match {
			case Some(lastHealth) =>
				if (lastHealth != health) {
					// report change in health of component
					healthChangeSource.fire(health)
				}

			case None =>
				healthChangeSource.fire(health)
		}
	}

	/** @return A map containing the most up-to-date status for each trace component. */
	def currentHealth = statusMap.synchronized { statusMap.toMap }

	/** Looks up the current monitor data for a specific component */
	def currentMonitorData[T <: TraceComponentMonitorData](component: TraceComponent): Option[T] = {
		statusMap.synchronized { statusMap.get(component) } match {
			case Some(data) => Some(data.asInstanceOf[T])
			case None => None
		}
	}

	/** Add a HealthMonitor, scheduling it to run repeatedly at its own decided interval,
	  * after an immediate run.
	  * When it runs, any reported `healthUpdateEvent` that it triggers will be propagated
	  * into this TraceStatus's internal map.
	  */
	def addHealthMonitor(monitor: HealthMonitor): Unit = {
		monitor.healthUpdates += reportHealth
		executor.scheduleAtFixedRate(monitor, 0, monitor.runInterval, TimeUnit.MILLISECONDS)
	}

	//	/** Removes a HealthMonitor from this TraceStatus, so that its health updates will no longer
	//	  * be propagated into the internal health map.
	//	  */
	//	def -=(monitor: HealthMonitor) {
	//		//		monitor.healthUpdateEvent -= reportHealth
	//		executor.remove(monitor)
	//	}

	/** Stop updating the health map and trigger the shutdown. */
	def cleanup = executor.shutdown
}
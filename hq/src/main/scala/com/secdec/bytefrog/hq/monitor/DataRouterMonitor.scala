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

import com.codedx.codepulse.hq.data.processing.DataRouter

/** Gives the current number of processed events and processing event rate */
case class DataRouterMonitorData(eventsProcessed: Long, currentRate: Integer, timestamp: Long)
	extends TraceComponentMonitorData

/** A HealthMonitor that monitors a DataRouter, reporting health status based on the number of processed
  * events.
  *
  * @author robertf
  */
class DataRouterMonitor(router: DataRouter) extends HealthMonitor {
	override val runInterval = 500 // run 2x a second

	private implicit val component = DataRouterComponent
	private var lastCount = 0L
	private var lastRun = 0L

	def checkHealth = {
		val time = System.currentTimeMillis

		val processed = router.messagesRouted
		val rate = if (lastRun > 0) {
			val deltaTime = time - lastRun
			val deltaCt = processed - lastCount
			(1000 * (deltaCt.toDouble / deltaTime)).toInt
		} else 0

		lastCount = processed
		lastRun = time

		healthy(DataRouterMonitorData(processed, rate, time))
	}
}
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

package com.codedx.codepulse.hq.monitor

import com.codedx.codepulse.hq.agent.AgentController
import com.codedx.codepulse.hq.agent.AgentState
import com.codedx.codepulse.utility.Loggable

case class AgentHealthMonitorData(agentState: AgentState) extends TraceComponentMonitorData

/** A health monitor to keep an eye on Agent/AgentController.
  *
  * @param controller the agent controller to monitor
  * @param heartbeatInterval The number of milliseconds expected between each Agent Heartbeat
  * @param maxMissedHeartbeats The number of heartbeats that may be missed before the Agent
  * is considered "dead"
  * @param modeChangeDelay The number of milliseconds allowed before the Agent acknowledges
  * a change in mode (paused, suspended, etc) that was instructed by HQ.
  */
class AgentHealthMonitor(
	controller: AgentController,
	heartbeatInterval: Integer,
	maxMissedHeartbeats: Integer,
	modeChangeDelay: Integer)
	extends HealthMonitor with Loggable {

	override val runInterval = 1000 // run every second

	private implicit val component = AgentComponent

	private var i = 0

	def checkHealth = {
		if (controller.lastHeartbeat != null) {
			i += 1
			if (i % 10 == 0) // print status every 10s
				logger.debug(s"Agent is in ${controller.lastHeartbeat.operationMode}, [expected ${controller.currentState}], send queue size = ${controller.lastHeartbeat.sendQueueSize}")
		}

		val time = System.currentTimeMillis

		// initially assume things are looking good
		val data = AgentHealthMonitorData(controller.currentState)
		var health = healthy(data)

		/*
		 * If the controller thinks things are in the wrong mode, and it's been a while since the latest
		 * state change was requested, report a "concerned" health status.
		 */
		if (!controller.wasLastHeartbeatModeExpected && time > (controller.lastStateChange + modeChangeDelay)) {
			health = concerned(s"Agent is not in the expected mode after ${time - controller.lastStateChange} ms.", data)
		}

		if (controller.currentState != AgentState.ShuttingDown) {
			val timeSinceHeartbeat = time - controller.lastHeartbeatTime
			val missedHeartbeats = timeSinceHeartbeat / heartbeatInterval

			// If there haven't been any heartbeats in a while, the Agent is likely dead.
			if (missedHeartbeats > maxMissedHeartbeats) {
				health = unhealthy(s"No heartbeat received for ${missedHeartbeats * heartbeatInterval} ms.", data)
			}
		}

		health
	}
}
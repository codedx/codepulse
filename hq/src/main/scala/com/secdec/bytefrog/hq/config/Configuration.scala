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

package com.codedx.codepulse.hq.config

import com.codedx.codepulse.agent.common.config.RuntimeAgentConfigurationV1

/** Builds a RuntimeAgentConfiguration with a given trace configuration and agent
  * configuration.
  */
object Configuration {
	def apply(runId: Byte, traceSettings: TraceSettings, agentConfiguration: AgentConfiguration): RuntimeAgentConfigurationV1 = {

		val exclusions = new java.util.ArrayList[String]
		val inclusions = new java.util.ArrayList[String]

		for (exc <- traceSettings.exclusions) exclusions add exc
		for (inc <- traceSettings.inclusions) inclusions add inc

		new RuntimeAgentConfigurationV1(
			runId,
			agentConfiguration.heartbeatInterval,
			exclusions,
			inclusions,
			agentConfiguration.bufferMemoryBudget,
			agentConfiguration.poolRetryCount,
			agentConfiguration.numDataSenders)
	}
}
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

import com.secdec.bytefrog.hq.connect.SocketServer
import com.secdec.bytefrog.hq.config.AgentConfiguration
import com.secdec.bytefrog.hq.config.HQConfiguration
import com.secdec.bytefrog.hq.config.MonitorConfiguration
import com.secdec.bytefrog.hq.trace.Trace
import net.liftweb.http.LiftRules
import bootstrap.liftweb.AppCleanup
import com.secdec.codepulse.data.jsp.JspMapper
import com.secdec.codepulse.data.model.ProjectData

object TraceServer {

	// listen for incoming connections on port 8765
	private implicit lazy val socketServer = {
		val ss = SocketServer.default(com.secdec.codepulse.userSettings.tracePort)
		ss.start()
		AppCleanup.add { () =>
			ss.shutdown
			println("Shutdown TracerServer's socketServer")
		}
		ss
	}

	def port = socketServer.port
	def setPort(newPort: Int) = socketServer.setPort(newPort)

	def awaitNewTrace(projectData: ProjectData, jspMapper: Option[JspMapper]) = {
		val agentConfig = AgentConfiguration()
		val hqConfig = HQConfiguration()
		val monitorConfig = MonitorConfiguration()

		val configProvider = () => {
			val traceSettings = TraceSettingsCreator.generateTraceSettings(projectData, jspMapper)
			(traceSettings, agentConfig, hqConfig, monitorConfig)
		}

		Trace.getTrace(configProvider)
	}
}
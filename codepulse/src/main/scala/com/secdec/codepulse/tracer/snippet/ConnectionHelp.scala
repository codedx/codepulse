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

package com.secdec.codepulse.tracer.snippet

import net.liftweb.http.DispatchSnippet
import scala.xml.NodeSeq
import java.io.File
import net.liftweb.util.BindHelpers._
import scala.xml.Text

object ConnectionHelp extends DispatchSnippet {

	def dispatch = {
		case "render" => doRender
	}

	def doRender(template: NodeSeq): NodeSeq = bind("help", template, {
		"agentcommand" -> { (xml: NodeSeq) => Text(traceAgentCommand) }
	})

	def traceAgentCommand = {

		var agentPath = new File("../tracers/java/agent.jar").getCanonicalPath
		if (agentPath.contains("/app.nw")) {
			// on macOS, the tracers folder sits alongside the "Code Pulse.app" folder
			agentPath = new File("../../../../../tracers/java/agent.jar").getCanonicalPath
		}

		val hqAddress = "localhost"
		val hqPort = com.secdec.codepulse.userSettings.tracePort

		val cmd = s"-javaagent:$agentPath=$hqAddress:$hqPort"

		// if `cmd` has spaces, wrap it in "quotes"
		if (cmd.contains(" ")) '"' + cmd + '"' else cmd
	}
}

object DotNETIISHelp extends DispatchSnippet {

	def dispatch = {
		case "render" => doRender
	}

	def doRender(template: NodeSeq): NodeSeq = bind("help", template, {
		"dotnetiisagentcommand" -> { (xml: NodeSeq) => Text(dotNETTraceCommandForIIS) }
	})

	def dotNETTraceCommandForIIS = {
		val hqPort = com.secdec.codepulse.userSettings.tracePort
		val backslash = "\\"
		val cmd = s"""CodePulse.DotNet.Tracer.exe -IIS "-TargetDir:<targetdir>" "-IISAppPoolIdentity:<domain${backslash}username>" -CodePulsePort:$hqPort"""

		cmd
	}
}

object DotNETExecutableHelp extends DispatchSnippet {

	def dispatch = {
		case "render" => doRender
	}

	def doRender(template: NodeSeq): NodeSeq = bind("help", template, {
		"dotnetexecutableagentcommand" -> { (xml: NodeSeq) => Text(dotNETTraceCommandForExecutable) }
	})

	def dotNETTraceCommandForExecutable = {
		val hqPort = com.secdec.codepulse.userSettings.tracePort
		val cmd = s"""CodePulse.DotNet.Tracer.exe "-Target:<target application>" -SendVisitPointsTimerInterval:<milliseconds> -CodePulsePort:$hqPort"""

		cmd
	}
}
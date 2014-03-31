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

import com.secdec.bytefrog.hq.config.TraceSettings
import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.trace.TraceData

object TraceSettingsCreator {

	def generateTraceSettings(traceData: TraceData): TraceSettings = {
		val inclusions = traceData.treeNodeData.iterate {
			for {
				treeNode <- _
				if treeNode.kind == CodeTreeNodeKind.Pkg
			} yield {
				val slashedName = treeNode.label.replace('.', '/')
				"^" + slashedName + "/[^/]+$"
			}
		}

		TraceSettings(exclusions = List(".*"), inclusions.toList)
	}
}
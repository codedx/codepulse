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

import com.secdec.codepulse.data.trace.TraceDataProvider
import com.secdec.codepulse.data.trace.TraceId
import com.secdec.codepulse.data.trace.TreeBuilder

/** Manages a single TreeBuilder instance, such that we're only holding an
  * entire trace tree for a single trace at a time.
  *
  * @author robertf
  */
class TreeBuilderManager(dataProvider: TraceDataProvider) {
	private val lock = new Object
	private var cachedId = None: Option[TraceId]
	private var current = None: Option[TreeBuilder]

	private def changeTrace(newTrace: TraceId) = {
		cachedId = None
		val data = dataProvider.getTrace(newTrace)
		val builder = new TreeBuilder(data.treeNodeData)
		current = Some(builder)
		cachedId = Some(newTrace)
		builder
	}

	def get(trace: TraceId): TreeBuilder = {
		lock.synchronized {
			if (cachedId.exists(_ == trace))
				current.getOrElse(changeTrace(trace))
			else
				changeTrace(trace)
		}
	}

	def clear() {
		lock.synchronized {
			cachedId = None
			current = None
		}
	}
}
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

package com.secdec.codepulse.data.trace

import scala.concurrent.duration._
import akka.actor.ActorSystem

/** Main entry point for getting a `TraceData` for a certain trace ID.
  *
  * @author robertf
  */
trait TraceDataProvider {
	def getTrace(id: TraceId): TraceData
	def removeTrace(id: TraceId): Unit
	def traceList: List[TraceId]
}

/** Provides instances of the default TraceDataProvider implementation.
  *
  * @author robertf
  */
object TraceDataProvider {
	lazy val DefaultStorageDir = {
		val basePath = com.secdec.codepulse.paths.appData
		val dir = new java.io.File(basePath, "codepulse-traces")
		dir.mkdirs
		dir
	}
}

/** Access trait for complete set of trace data.
  *
  * @author robertf
  */
trait TraceData {
	def metadata: TraceMetadata
	def treeNodeData: TreeNodeDataAccess
	def recordings: RecordingMetadataAccess
	def encounters: TraceEncounterDataAccess

	/** Flush any buffered data to the backing data store without detaching. */
	def flush(): Unit

	/** Cleanly detach from whatever backing data store is in place. */
	def close(): Unit
}
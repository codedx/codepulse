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

package com.secdec.codepulse.data.model

/** Main entry point for getting a `ProjectData` for a certain trace ID.
  *
  * @author robertf
  */
trait ProjectDataProvider {
	def getProject(id: ProjectId): ProjectData
	def removeProject(id: ProjectId): Unit
	def projectList: List[ProjectId]
	def maxProjectId: Int
}

/** Provides instances of the default ProjectDataProvider implementation.
  *
  * @author robertf
  */
object ProjectDataProvider {
	lazy val DefaultStorageDir = {
		val basePath = com.secdec.codepulse.paths.appData
		val dir = new java.io.File(basePath, "project-data")
		dir.mkdirs
		dir
	}
}

/** Access trait for complete set of project data.
  *
  * @author robertf
  */
trait ProjectData {
	def id: ProjectId

	def metadata: ProjectMetadata
	def treeNodeData: TreeNodeDataAccess
	def sourceData: SourceDataAccess
	def recordings: RecordingMetadataAccess
	def encounters: TraceEncounterDataAccess

	/** Flush any buffered data to the backing data store without detaching. */
	def flush(): Unit

	/** Cleanly detach from whatever backing data store is in place. */
	def close(): Unit
}
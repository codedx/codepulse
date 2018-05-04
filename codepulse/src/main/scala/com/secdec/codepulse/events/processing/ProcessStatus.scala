/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2017 Applied Visions - http://securedecisions.avi.com
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

package com.secdec.codepulse.processing

import java.io.File

import com.secdec.codepulse.data.model.{ SourceDataAccess, TreeNodeDataAccess }
import com.secdec.codepulse.data.storage.Storage

sealed trait ProcessStatus
sealed trait TransientProcessStatus extends ProcessStatus
final case class ProcessEnvelope(topic: String, processStatus: ProcessStatus)

object ProcessStatus {
	case class Queued(identifier: String) extends TransientProcessStatus
	case class Running(identifier: String) extends TransientProcessStatus
	case class Finished(identifier: String, payload: Option[AnyRef]) extends ProcessStatus
	case class Failed(identifier: String, action: String, payload: Option[Exception]) extends ProcessStatus
	case class NotRun(identifier: String) extends ProcessStatus
	case class Unknown(identifier: String) extends ProcessStatus

	case class DataInputAvailable(identifier: String, storage: Storage, treeNodeData: TreeNodeDataAccess, sourceData: SourceDataAccess, post: () => Unit) extends ProcessStatus
	case class ProcessDataAvailable(identifier: String, storage: Storage, treeNodeData: TreeNodeDataAccess, sourceData: SourceDataAccess) extends ProcessStatus
	case class PostProcessDataAvailable(identifier: String, payload: Option[AnyRef]) extends ProcessStatus

	implicit def asEnvelope(processStatus: ProcessStatus): ProcessEnvelope = {
		processStatus match {
			case status: Queued => ProcessEnvelope("Queued", status)
			case status: Running => ProcessEnvelope("Running", status)
			case status: Finished => ProcessEnvelope("Finished", status)
			case status: Failed => ProcessEnvelope("Failed", status)
			case status: NotRun => ProcessEnvelope("NotRun", status)
			case status: Unknown => ProcessEnvelope("Unknown", status)
			case status: DataInputAvailable => ProcessEnvelope("DataInputAvailable", status)
			case status: ProcessDataAvailable => ProcessEnvelope("ProcessDataAvailable", status)
			case status: PostProcessDataAvailable => ProcessEnvelope("PostProcessDataAvailable", status)
		}
	}
}
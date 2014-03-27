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

package com.secdec.codepulse.data.trace.slick

import scala.slick.driver.JdbcProfile
import scala.slick.jdbc.JdbcBackend.Database

import com.secdec.codepulse.data.trace._

/** Main access to trace data, using Slick, with `db` and `driver`.
  *
  * @author robertf
  */
private[slick] class SlickTraceData(val db: Database, val driver: JdbcProfile, encounterBufferSize: Int) extends TraceData {
	private val traceMetadataDao = new TraceMetadataDao(driver)
	private val metadataAccess = new SlickTraceMetadataAccess(traceMetadataDao, db) with DefaultTraceMetadataUpdates with TraceMetadata

	private val treeNodeDataDao = new TreeNodeDataDao(driver)
	private val treeNodeDataAccess = new SlickTreeNodeDataAccess(treeNodeDataDao, db)

	private val recordingMetadataDao = new RecordingMetadataDao(driver)
	private val recordingMetadataAccess = new SlickRecordingMetadataAccess(recordingMetadataDao, db)

	private val encountersDao = new EncountersDao(driver, recordingMetadataDao, treeNodeDataDao)
	private val encountersAccess = new SlickTraceEncounterDataAccess(encountersDao, db, encounterBufferSize)

	def metadata: TraceMetadata = metadataAccess
	def treeNodeData: TreeNodeDataAccess = treeNodeDataAccess
	def recordings: RecordingMetadataAccess = recordingMetadataAccess
	def encounters: TraceEncounterDataAccess = encountersAccess

	/** Initialize a blank DB for use. */
	private[slick] def init() = db withTransaction { implicit transaction =>
		traceMetadataDao.create
		treeNodeDataDao.create
		recordingMetadataDao.create
		encountersDao.create
	}

	def flush() {
		encountersAccess.flush
	}

	def close() {
		flush
	}
}
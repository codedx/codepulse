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

import java.io.File

import scala.concurrent.duration._
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.Database

import com.secdec.codepulse.data.trace._
import com.secdec.codepulse.util.RichFile.RichFile

import akka.actor.ActorSystem

/** Provides `SlickTraceData` instances for traces, basing storage in `folder`.
  * Uses H2 for data storage.
  *
  * @author robertf
  */
class SlickH2TraceDataProvider(folder: File, actorSystem: ActorSystem) extends TraceDataProvider {
	private val EncountersBufferSize = 500
	private val EncountersFlushInterval = 1.second

	private val cache = collection.mutable.Map.empty[TraceId, TraceData]

	private object TraceFilename {
		def apply(traceId: TraceId) = s"${getDbName(traceId)}.h2.db"
		def getDbName(traceId: TraceId) = s"trace-${traceId.num}"

		val NameRegex = raw"trace-(\d+)\.h2.db".r

		def unapply(filename: String): Option[TraceId] = filename match {
			case NameRegex(TraceId(id)) => Some(id)
			case _ => None
		}
	}

	def getTrace(id: TraceId): TraceData = cache.getOrElseUpdate(id, {
		val needsInit = !(folder / TraceFilename(id)).exists

		val db = Database.forURL(s"jdbc:h2:file:${(folder / TraceFilename.getDbName(id)).getCanonicalPath};DB_CLOSE_DELAY=10", driver = "org.h2.Driver")
		val data = new SlickTraceData(db, H2Driver, EncountersBufferSize, EncountersFlushInterval, actorSystem)

		if (needsInit) data.init

		data
	})

	def removeTrace(id: TraceId) {
		for (cached <- cache get id) {
			cached.close
			cache -= id
		}

		val file = folder / TraceFilename(id)
		if (file.exists) file.delete
	}

	def traceList: List[TraceId] = {
		folder.listFiles.toList.map { _.getName } collect {
			case TraceFilename(id) => id
		}
	}
}
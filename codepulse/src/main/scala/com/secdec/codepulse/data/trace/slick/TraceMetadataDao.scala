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
import com.secdec.codepulse.data.trace._

/** The Slick DAO for trace metadata.
  *
  * @author robertf
  */
private[slick] class TraceMetadataDao(val driver: JdbcProfile) {
	import driver.simple._

	class TraceMetadata(tag: Tag) extends Table[(Int, String, String)](tag, "trace_metadata") {
		def traceId = column[Int]("trace_id", O.NotNull)
		def key = column[String]("key", O.NotNull)
		def value = column[String]("value", O.NotNull)
		def pk = primaryKey("pk_trace_metadata", (traceId, key))
		def * = (traceId, key, value)
	}
	val traceMetadata = TableQuery[TraceMetadata]

	def create(implicit session: Session) = traceMetadata.ddl.create

	def getMap()(implicit session: Session): Map[Int, Map[String, String]] = traceMetadata.list.groupBy(_._1) mapValues {
		entries => (entries map { case (_, key, value) => key -> value }).toMap
	}

	def get(traceId: Int, key: String)(implicit session: Session): Option[String] =
		(for (r <- traceMetadata if r.traceId === traceId && r.key === key) yield r.value).firstOption

	def set(traceId: Int, key: String, value: String)(implicit session: Session) {
		set(traceId, key, Some(value))
	}

	def set(traceId: Int, key: String, value: Option[String])(implicit session: Session) {
		value match {
			case Some(value) =>
				get(traceId, key) match {
					case Some(_) => (for (r <- traceMetadata if r.traceId === traceId && r.key === key) yield r.value).update(value)
					case None => traceMetadata += (traceId, key, value)
				}

			case None =>
				(for (r <- traceMetadata if r.traceId === traceId && r.key === key) yield r).delete
		}
	}

	def delete(traceId: Int)(implicit session: Session) {
		(for (r <- traceMetadata if r.traceId === traceId) yield r).delete
	}
}
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

import reactive.{ EventStream, EventSource }

/** Access trait for trace metadata.
  *
  * @author robertf
  */
trait TraceMetadataAccess {
	def name: String
	def name_=(newName: String): String

	def hasCustomName: Boolean

	def creationDate: Long
	def creationDate_=(newDate: Long): Long

	def importDate: Option[Long]
	def importDate_=(newDate: Option[Long]): Option[Long]
}

/** Access trait for trace metadata update events.
  *
  * @author robertf
  */
trait TraceMetadataUpdates {
	def nameChanges: EventStream[String]
}

/** Default stackable implementation that provides metadata update events.
  *
  * @author robertf
  */
private[trace] trait DefaultTraceMetadataUpdates extends TraceMetadataAccess with TraceMetadataUpdates {
	private val _nameChanges = new EventSource[String]

	def nameChanges: EventStream[String] = _nameChanges

	abstract override def name_=(newName: String): String = {
		val res = super.name = newName
		if (res == newName) _nameChanges fire res
		res
	}
}

/** The final trace metadata access that gets exposed.
  *
  * @author robertf
  */
trait TraceMetadata extends TraceMetadataAccess with TraceMetadataUpdates
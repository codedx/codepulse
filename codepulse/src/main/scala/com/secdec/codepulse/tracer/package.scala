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

package com.secdec.codepulse

import language.implicitConversions

package object tracer {

	class BootVar[T] {
		private var _value: Option[T] = None
		def apply() = _value getOrElse {
			throw new IllegalStateException("Code Pulse has not booted yet")
		}
		private[tracer] def set(value: T) = {
			_value = Some(value)
		}
	}

	implicit def bootVarToInstance[T](v: BootVar[T]): T = v.apply()

	val traceManager = new BootVar[TraceManager]
	val traceFileUploadServer = new BootVar[TraceFileUploadHandler]
	val traceAPIServer = new BootVar[TraceAPIServer]

	def boot() {
		val tm = TraceManager.default

		traceManager set tm
		traceFileUploadServer set new TraceFileUploadHandler(tm).initializeServer
		traceAPIServer set new TraceAPIServer(tm).initializeServer
	}
}
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

package com.secdec.codepulse.tracer.export

import java.io.InputStream
import java.io.OutputStream

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser

/** JSON Helpers for import/export purposes.
  *
  * @author robertf
  */
trait JsonHelpers {
	private val Json = new JsonFactory

	protected def streamJson[T](out: OutputStream, usePrettyPrint: Boolean = true)(f: JsonGenerator => T): T = {
		val jg = Json createGenerator out
		if (usePrettyPrint) jg.useDefaultPrettyPrinter

		try {
			f(jg)
		} finally jg.flush
	}

	protected def readJson(in: InputStream)(f: JsonParser => Unit) {
		val jp = Json createParser in

		try {
			f(jp)
		} finally jp.close
	}
}
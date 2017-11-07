/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
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

package com.secdec.bytefrog.hq.protocol

import java.io.DataInputStream
import java.io.DataOutputStream

/** Trait that describes an object that can read individual data events from an
  * input stream, piping them to an output stream and returning what type of
  * data event it read.
  *
  * Intended to be used by a DataController that needs to count events as they come
  * in, while sending them into a MessageQueue one at a time.
  */
trait DataEventReader {
	def readDataEvent(from: DataInputStream, to: DataOutputStream): DataEventType
}
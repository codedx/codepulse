/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.codedx.codepulse.hq.protocol

import java.io.DataInputStream

/** A "Push" API for parsing data events from a DataInputStream.
  * A DataMessageParser can parse an entire stream in one shot,
  * calling the appropriate methods on a given DataMessageHandler
  * for each message it parses.
  *
  * "Push" means that data is "pushed" from the parser to a handler
  * as it encounters new data. It is the opposite of a "pull" API,
  * where client code "pulls" data from the parser on demand. For
  * a "pull" API for parsing data messages, see `DataMessageReader`.
  */
trait DataMessageParser {

	/** Parse the entire `data` stream, using the callback methods
	  * on the given `handler` for each potential data message.
	  */
	def parse(data: DataInputStream, handler: DataMessageHandler, progressHandler: Long => Unit = _ => {}, parseDataBreaks: Boolean = false): Unit
}
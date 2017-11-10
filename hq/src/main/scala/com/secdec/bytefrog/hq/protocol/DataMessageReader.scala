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

/** A "Pull" API for reading data events from a DataInputStream.
  * A DataMessageReader can read individual DataMessages from a
  * stream, one at a time. Each message is returned as a `Data`,
  * containing the particular `DataMessage` representation of the
  * message. In case of errors and EOF, the `IO.Error` and `IO.EOF`
  * case classes will be returned.
  *
  * "Pull" means that data can be "pulled" from the reader at will,
  * and that reading a stream doesn't need to happen all at once.
  * It is the opposite of a "push" API, where the parser "pushes"
  * data to a handler, and parsing is done in one shot.
  */
trait DataMessageReader {

	/** Read the next data message from the stream, returning an
	  * in-memory representation of that message, inside of an
	  * `IO.Input` structure.
	  */
	def readMessage(stream: DataInputStream): IO.Input[DataMessage]
}
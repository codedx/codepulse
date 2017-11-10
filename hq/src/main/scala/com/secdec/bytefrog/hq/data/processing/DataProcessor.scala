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

package com.codedx.codepulse.hq.data.processing

import com.codedx.codepulse.hq.protocol.DataMessageContent
import com.codedx.codepulse.hq.trace.Cleanup

/** DataProcessor is a basic trait for any process that needs to be applied to the data. A DataProcessor
  * is, quite simply, sent a stream of data messages.
  *
  * DataMessageContent is streamed in, and can be handled at will. Anything that is sequence-sensitive
  * (such as method entries and exits) is guaranteed to be in order. Other unsequenced data (such as
  * thread and method name mappings) will arrive, but not in any sort of guaranteed order.
  *
  * @author robertf
  */
trait DataProcessor extends Cleanup {
	/** Process a single data message */
	def processMessage(message: DataMessageContent): Unit

	/** Process a break in the data */
	def processDataBreak(): Unit

	/** There is no more data, so do any cleanup/saving/etc necessary */
	def finishProcessing(): Unit
}
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

package com.codedx.codepulse.hq.data

import com.codedx.codepulse.hq.connect.DataConnection
import com.codedx.codepulse.hq.data.collection.DataCollector
import com.codedx.codepulse.hq.protocol.DataMessage
import com.codedx.codepulse.hq.protocol.DataMessageContent
import com.codedx.codepulse.hq.protocol.DefaultDataMessageHandler
import com.codedx.codepulse.hq.trace.players.LoopPlayer

class DataConnectionController(dataConnection: DataConnection, dataCollector: DataCollector)
	extends LoopPlayer {

	override def shutdown = {
		super.shutdown
		dataConnection.close // closing the connection breaks out of the readEvents in the loop
	}

	override def preLoop = {
		dataCollector.registerDataConnection(this)
	}

	// this loop will likely only ever run once, since readEvents will block until the end
	def doLoop = dataConnection.readEvents(new DefaultDataMessageHandler {
		import DataMessage._
		import DataMessageContent._

		override def handleMapThreadName(threadName: String, threadId: Int, timestamp: Int) {
			dataCollector ! UnsequencedData(MapThreadName(threadName, threadId, timestamp))
		}

		override def handleMapMethodSignature(methodSig: String, methodId: Int) {
			dataCollector ! UnsequencedData(MapMethodSignature(methodSig, methodId))
		}

		override def handleMapException(exception: String, exceptionId: Int) {
			dataCollector ! UnsequencedData(MapException(exception, exceptionId))
		}

		override def handleMethodEntry(methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int) {
			dataCollector ! SequencedData(timestamp, sequenceId, MethodEntry(methodId, timestamp, threadId))
		}

		override def handleMethodExit(methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int) {
			dataCollector ! SequencedData(timestamp, sequenceId, MethodExit(methodId, timestamp, lineNum, threadId))
		}

		override def handleExceptionMessage(exception: Int, methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int) {
			dataCollector ! SequencedData(timestamp, sequenceId, Exception(exception, methodId, timestamp, lineNum, threadId))
		}

		override def handleExceptionBubble(exception: Int, methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int) {
			dataCollector ! SequencedData(timestamp, sequenceId, ExceptionBubble(exception, methodId, timestamp, threadId))
		}

		override def handleMarkerMessage(timestamp: Int, sequence: Int, key: String, value: String) {
			dataCollector ! SequencedData(timestamp, sequence, Marker(key, value, timestamp))
		}

		override def handleParserError(error: Throwable) {
			dataCollector.reportConnectionError(DataConnectionController.this, error)
			shutdown
		}

		override def handleParserEOF {
			dataCollector.reportDataConnectionComplete(DataConnectionController.this)
			shutdown
		}
	})

	override def postLoop = {
		dataConnection.close
	}
}
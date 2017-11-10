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

package com.codedx.codepulse.hq.data.collection

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.Semaphore

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Queue

import com.codedx.codepulse.hq.data.DataConnectionController
import com.codedx.codepulse.hq.data.processing.DataRouter
import com.codedx.codepulse.hq.errors.TraceErrorController
import com.codedx.codepulse.hq.errors.UnexpectedError
import com.codedx.codepulse.hq.protocol.DataMessage
import com.codedx.codepulse.hq.trace.players.LoopPlayer

/** DataCollector is responsible for collecting semi-sorted data fed into it, and feeding it back out in
  * the proper order.
  *
  * Input data is "semi-sorted," which means the next data point should always be relayed before the
  * buffer fills up. We should always be able to give the next data point before the bounded priority
  * queue fills up. This "semi-sorted" requirement matches the behavior of the buffer pool on Agent.
  *
  * DataCollector also tracks the lifetime of the data connections that are feeding it. This allows
  * proper reaction to take place when data connections close. Lifetime of this player is trickled down
  * from the data connections. We poison ourself and the data router when all data connections have
  * ended, so we need not be concerned with player stopping. For immediate halt cases, cleanup is
  * implemented.
  *
  * @author robertf
  */
class DataCollector(traceErrorController: TraceErrorController, dataRouter: DataRouter, initialSortQueueSize: Int, maximumDataQueueSize: Int)
	extends LoopPlayer {

	private val dataQueue = new ConcurrentLinkedQueue[DataMessage]
	private val dataQueueWriteSem = new Semaphore(maximumDataQueueSize)
	private val dataQueueReadSem = new Semaphore(0)
	private var complete = false

	private val dataBreaks = Queue[Int]()

	private var currentSeq = 0;
	private val sortQueue = new PriorityBlockingQueue[DataMessage.SequencedData](initialSortQueueSize, DataOrdering)

	private val connections = ArrayBuffer.empty[DataConnectionController]

	def registerDataConnection(connection: DataConnectionController) = connections.synchronized {
		connections += connection
	}

	def reportConnectionError(connection: DataConnectionController, error: Throwable) {
		traceErrorController.reportTraceError(UnexpectedError("Data connection encountered a problem", Some(error)))
	}

	def reportDataConnectionComplete(connection: DataConnectionController) = {
		val done = connections.synchronized {
			connections -= connection
			connections.isEmpty
		}

		if (done) {
			// signal that we're complete
			complete = true
			dataQueueReadSem.release
		}
	}

	def !(message: DataMessage) {
		dataQueueWriteSem.acquire
		dataQueue offer message
		dataQueueReadSem.release
	}

	def reportDataBreak(sequence: Int) {
		for (last <- dataBreaks.lastOption) assert(sequence > last)
		if (sequence <= currentSeq) throw new IllegalStateException("Told about a data break too late")
		dataBreaks enqueue sequence
	}

	protected def doLoop = {
		dataQueueReadSem.acquire
		if (!complete) {
			dataQueue.poll match {
				case d: DataMessage.SequencedData =>
					sortQueue put d
					pumpQueue

				case d: DataMessage.UnsequencedData =>
					routeMessage(d)
			}

			dataQueueWriteSem.release
		} else {
			// we're complete, so we make sure to drain the data queue
			while (dataQueueReadSem.tryAcquire) {
				dataQueue.poll match {
					case d: DataMessage.SequencedData =>
						sortQueue put d

					case d: DataMessage.UnsequencedData =>
						routeMessage(d)
				}

				dataQueueWriteSem.release
			}

			finishProcessing
		}
	}

	private def pumpQueue {
		while (!sortQueue.isEmpty && sortQueue.peek.sequence == currentSeq) {
			for (nextBreak <- dataBreaks.headOption)
				if (currentSeq == nextBreak) {
					dataBreaks.dequeue
					dataRouter.routeDataBreak
				}

			currentSeq += 1
			routeMessage(sortQueue.take)
		}
	}

	private def finishProcessing {
		// pump the queue one last time
		pumpQueue

		// make sure the sort queue is empty (it should be)
		if (!sortQueue.isEmpty)
			traceErrorController.reportTraceError(UnexpectedError("Incomplete data detected (data queue not empty after processing ended)."))

		dataRouter.finish
		shutdown
	}

	private def routeMessage(message: DataMessage) = dataRouter route message.content
}
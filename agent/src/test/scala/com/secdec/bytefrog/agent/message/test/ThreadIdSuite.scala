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

package com.secdec.bytefrog.agent.message.test

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

import org.scalatest.FunSpec
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.matchers.ShouldMatchers
import org.scalamock.scalatest.MockFactory

import com.secdec.bytefrog.agent.message._
import com.secdec.bytefrog.agent.util.MockHelpers
import com.secdec.bytefrog.common.message._
import com.secdec.bytefrog.common.queue.DataBufferOutputStream

class ThreadIdSuite extends FunSpec with ShouldMatchers with AsyncAssertions with MockFactory with MockHelpers {

	class FakeBufferService extends BufferService {
		def innerObtain = new DataBufferOutputStream(new ByteArrayOutputStream)
		def innerSend(buffer: DataBufferOutputStream) = ()
	}

	/** Runs an action on a different thread.
	  * This method will wait for the thread to complete, so don't use this for Async stuff.
	  */
	def doOnSeparateThread(body: => Unit) = {
		val r = new Runnable {
			def run = body
		}
		val t = new Thread(r)
		t.start
		t.join
	}

	describe("ThreadId.getCurrent") {
		it("Should return the same id for a thread even when it changes names") {
			val protocol = mock[MessageProtocol]
			val md = new MessageDealer(protocol, new FakeBufferService)

			(protocol.writeMapMethodSignature _).expects(*, *, *).anyNumberOfTimes
			(protocol.writeMethodEntry _).expects(*, *, *, *, *).anyNumberOfTimes

			val w = new Waiter

			doOnSeparateThread {
				var id1: Int = -1
				var id2: Int = -1
				inSequence {
					(protocol.writeMapThreadName _).expects(*, *, *, *).once.onCall {
						(_: DataOutputStream, threadId: Int, _: Int, _: String) =>
							id1 = threadId
					}

					(protocol.writeMapThreadName _).expects(*, *, *, *).once.onCall {
						(_: DataOutputStream, threadId: Int, _: Int, _: String) =>
							id2 = threadId
					}
				}
				val t = Thread.currentThread
				t.setName("Thread-1")
				md.sendMethodEntry("method")
				t.setName("Thread-A")
				md.sendMethodEntry("method")

				id1 should not equal -1
				id1 should equal(id2)

				w.dismiss()

			}

			w.await(dismissals(1))
		}

		it("Should give different ids for different threads, even if the threads have the same name") {
			val protocol = mock[MessageProtocol]
			val md = new MessageDealer(protocol, new FakeBufferService)

			(protocol.writeMapMethodSignature _).expects(*, *, *).anyNumberOfTimes
			(protocol.writeMethodEntry _).expects(*, *, *, *, *).anyNumberOfTimes

			//make a thread and get its id according to `md`
			var id1: Int = -1
			doOnSeparateThread {
				Thread.currentThread.setName("The Real Thread")
				(protocol.writeMapThreadName _).expects(*, *, *, *).once.onCall {
					(_: DataOutputStream, threadId: Int, _: Int, _: String) =>
						id1 = threadId
				}
				md.sendMethodEntry("method")
			}

			//make a second thread and get its id according to `md`
			var id2: Int = -1
			doOnSeparateThread {
				Thread.currentThread.setName("The Real Thread")
				(protocol.writeMapThreadName _).expects(*, *, *, *).once.onCall {
					(_: DataOutputStream, threadId: Int, _: Int, _: String) =>
						id2 = threadId
				}
				md.sendMethodEntry("method")
			}

			// the two ids should be different
			id1 should not equal (-1)
			id2 should not equal (-1)
			id1 should not equal (id2)
		}
	}

	describe("ThreadId's generated messages") {
		it("Should generate a message the first time it maps each thread") {
			val protocol = mock[MessageProtocol]
			val md = new MessageDealer(protocol, new FakeBufferService)

			doOnSeparateThread {
				val threadName = Thread.currentThread.getName
				(protocol.writeMapThreadName _).expects(*, *, *, threadName).once
				(protocol.writeMapMethodSignature _).expects(*, *, *).anyNumberOfTimes
				(protocol.writeMethodEntry _).expects(*, *, *, *, *).anyNumberOfTimes

				md.sendMethodEntry("method") //emit
				md.sendMethodEntry("method") //no emit
				md.sendMethodEntry("method") //no emit
			}
		}

		it("Should generate a message whenever it maps a thread whose name has changed") {
			val protocol = mock[MessageProtocol]
			val md = new MessageDealer(protocol, new FakeBufferService)
			(protocol.writeMapMethodSignature _).expects(*, *, *).anyNumberOfTimes
			(protocol.writeMethodEntry _).expects(*, *, *, *, *).anyNumberOfTimes

			doOnSeparateThread {
				//get the current id/name
				val threadName = Thread.currentThread.getName
				(protocol.writeMapThreadName _).expects(*, *, *, threadName).once
				//val id = mapper.getCurrent
				md.sendMethodEntry("method1")

				//change name and get the id
				(protocol.writeMapThreadName _).expects(*, *, *, "Thread-A").once
				Thread.currentThread.setName("Thread-A")
				md.sendMethodEntry("method2")

				//change name and get the id (again)
				(protocol.writeMapThreadName _).expects(*, *, *, "Thread-B").once
				Thread.currentThread.setName("Thread-B")
				md.sendMethodEntry("method3")
			}
		}
	}

}
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

package com.secdec.bytefrog.common.queue.test

import org.scalatest.FunSpec
import org.scalatest.concurrent.Conductors
import org.scalatest._
import org.scalatest.Matchers._
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import com.codedx.codepulse.agent.common.queue.BufferPool

class BufferPoolSpec extends FunSpec with Matchers with Conductors {
	override implicit def patienceConfig = PatienceConfig(timeout = 5000.millis)

	describe("BufferPool") {
		it("should start empty") {
			val pool = new BufferPool(5, 5)

			pool.isEmpty should be(true)
		}

		it("should start with zero readable buffers") {
			val pool = new BufferPool(5, 5)

			pool.numReadableBuffers should be(0)
		}

		it("should start full of writable buffers") {
			val pool = new BufferPool(10, 5)

			pool.numWritableBuffers should be(10)
		}

		describe("acquireForWriting") {
			it("should return a buffer immediately when one is available") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(1, 5)

				thread("writer") {
					val buffer = pool.acquireForWriting
					buffer should not be (null)
				}

				whenFinished {
					pool.numWritableBuffers should be(0)
				}
			}

			it("should block when no buffers are available") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(1, 5)

				thread("writer-1") {
					val buffer = pool.acquireForWriting
					buffer should not be (null)

					waitForBeat(2)
					pool.release(buffer)
				}

				thread("writer-2") {
					waitForBeat(1)

					val buffer = pool.acquireForWriting
					buffer should not be (null)

					beat should be(2)
				}

				whenFinished {
					pool.numWritableBuffers should be(0)
				}
			}

			it("should favor partially filled buffers") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(2, 5)

				thread("writer") {
					val buffer1 = pool.acquireForWriting
					buffer1 should not be (null)

					buffer1.write(0)
					pool.release(buffer1)

					val buffer2 = pool.acquireForWriting
					buffer2 should not be (null)
					buffer2.size should be > (0)
					pool.release(buffer2)
				}

				whenFinished {
					pool.numWritableBuffers should be(2)
					pool.numReadableBuffers should be(1)
				}
			}

			it("should not return a full buffer") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(2, 4)

				thread("writer") {
					val buffer1 = pool.acquireForWriting
					buffer1 should not be (null)

					buffer1.writeLong(0)
					pool.release(buffer1)

					val buffer2 = pool.acquireForWriting
					buffer2 should not be (null)
					buffer2.size should be(0)
					pool.release(buffer2)
				}

				whenFinished {
					pool.numWritableBuffers should be(1)
					pool.numReadableBuffers should be(1)
				}
			}

			it("should immediately return null when writing is disabled") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(1, 5)
				pool.setWriteDisabled(true)

				thread("writer") {
					val buffer = pool.acquireForWriting
					buffer should be(null)
				}
			}

			it("should return null if writing becomes disabled while blocking") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(1, 5)

				thread("writer-1") {
					val buffer = pool.acquireForWriting
					buffer should not be (null)
				}

				thread("writer-2") {
					waitForBeat(1)

					val buffer = pool.acquireForWriting
					buffer should be(null)

					beat should be(2)
				}

				thread("controller") {
					waitForBeat(2)

					pool.setWriteDisabled(true)
				}

				whenFinished {
					pool.numWritableBuffers should be(0)
				}
			}
		}

		describe("acquireForReading") {
			it("should return a buffer immediately when one is available") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(1, 5)

				thread("writer") {
					val buffer = pool.acquireForWriting
					buffer should not be (null)
					buffer.write(42)
					pool.release(buffer)

					waitForBeat(2)
				}

				thread("reader") {
					waitForBeat(1)

					val buffer = pool.acquireForReading
					buffer should not be (null)
					buffer.size should be > (0)

					waitForBeat(2)
				}

				whenFinished {
					pool.numReadableBuffers should be(0)
				}
			}

			it("should block when no buffers are available") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(1, 5)

				thread("writer") {
					val buffer = pool.acquireForWriting
					buffer should not be (null)
					buffer.write(7861)

					beat should be(0)
					waitForBeat(2)

					pool.release(buffer)

					waitForBeat(3)
				}

				thread("reader") {
					waitForBeat(1)

					val buffer = pool.acquireForReading
					buffer should not be (null)
					buffer.size should be > (0)

					waitForBeat(3)
				}

				whenFinished {
					pool.numWritableBuffers should be(0)
				}
			}

			it("should favor full buffers") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(2, 4)

				thread("writer") {
					val buffer1 = pool.acquireForWriting
					buffer1 should not be (null)
					buffer1.writeInt(4353)

					waitForBeat(1)

					val buffer2 = pool.acquireForWriting
					buffer2 should not be (null)
					buffer2.writeShort(2)

					pool.release(buffer2)
					pool.release(buffer1)

					beat should be(1)

					waitForBeat(4)
				}

				thread("reader") {
					waitForBeat(2)

					pool.numWritableBuffers should be(1)
					pool.numReadableBuffers should be(2)

					val buffer1 = pool.acquireForReading
					buffer1.size should be(4)

					waitForBeat(3)

					pool.numWritableBuffers should be(1)
					pool.numReadableBuffers should be(1)

					val buffer2 = pool.acquireForReading
					buffer2.size should be(2)

					waitForBeat(4)
				}

				whenFinished {
					pool.numWritableBuffers should be(0)
					pool.numReadableBuffers should be(0)
				}
			}

			it("should not return an empty buffer") {
				val conductor = new Conductor
				import conductor._

				val pool = new BufferPool(1, 5)

				thread("reader") {
					val buffer = pool.acquireForReading
					buffer should not be (null)
					buffer.size should be > (0)

					beat should be(1)
				}

				thread("writer") {
					waitForBeat(1)

					val buffer = pool.acquireForWriting
					buffer should not be (null)

					buffer.writeInt(1234)
					pool.release(buffer)
				}

				whenFinished {
					pool.numWritableBuffers should be(0)
					pool.numReadableBuffers should be(0)
				}
			}
		}
	}
}
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

package com.secdec.bytefrog.agent.message.test

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

import scala.collection.mutable.ListBuffer

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalamock.scalatest.MockFactory

import com.secdec.bytefrog.agent.message.BufferService
import com.secdec.bytefrog.agent.message.MessageDealer
import com.secdec.bytefrog.common.message.MessageProtocol
import com.secdec.bytefrog.common.queue.DataBufferOutputStream

class MethodIdSpec extends FunSpec with ShouldMatchers with MockFactory {

	class FakeBufferService extends BufferService {
		def innerObtain = new DataBufferOutputStream(new ByteArrayOutputStream)
		def innerSend(buffer: DataBufferOutputStream) = ()
	}

	def doMapMethodNames(names: String*): List[Int] = {
		val protocol = mock[MessageProtocol]
		val md = new MessageDealer(protocol, new FakeBufferService)

		(protocol.writeMapMethodSignature _).expects(*, *, *).anyNumberOfTimes
		(protocol.writeMapThreadName _).expects(*, *, *, *).anyNumberOfTimes

		var ids = new ListBuffer[Int]

		(protocol.writeMethodEntry _).expects(*, *, *, *, *).anyNumberOfTimes.onCall {
			(_: DataOutputStream, _: Int, _: Int, id: Int, _: Int) =>
				ids += id; ()
		}

		for (name <- names) md.sendMethodEntry(name)

		ids.result
	}

	describe("MethodId `get`") {

		it("should yield the same result if called repeatedly with the same inputs") {
			val ids = doMapMethodNames("A", "A")

			ids match {
				case a1 :: a2 :: Nil => a1 shouldBe a2
				case _ => fail
			}
		}

		it("should have consistent results even when called with new inputs") {
			doMapMethodNames("A", "B", "A") match {
				case a1 :: b :: a2 :: Nil =>
					a1 should equal(a2)
					a1 should not equal (b)
				case _ => fail
			}
		}
		it("should have consistent results across many inputs") {
			def getList = doMapMethodNames("A", "B", "C", "D")

			val r1 = getList
			val r2 = getList

			r1 should equal(r2)
		}
	}

	describe("MethodId.Mapper") {

		it("should fire a `MapMethodSignature` event the first time it maps a new String") {
			val protocol = mock[MessageProtocol]
			val md = new MessageDealer(protocol, new FakeBufferService)

			(protocol.writeMapThreadName _).expects(*, *, *, *).anyNumberOfTimes
			(protocol.writeMethodEntry _).expects(*, *, *, *, *).anyNumberOfTimes

			(protocol.writeMapMethodSignature _).expects(*, *, *).once

			md.sendMethodEntry("A")
		}

		it("should not fire a `MapMethodSignature` event more than once per String (when an infinite cache is used)") {
			val protocol = mock[MessageProtocol]
			val md = new MessageDealer(protocol, new FakeBufferService)

			(protocol.writeMapThreadName _).expects(*, *, *, *).anyNumberOfTimes
			(protocol.writeMethodEntry _).expects(*, *, *, *, *).anyNumberOfTimes

			(protocol.writeMapMethodSignature _).expects(*, *, "A").once
			(protocol.writeMapMethodSignature _).expects(*, *, "B").once
			(protocol.writeMapMethodSignature _).expects(*, *, "C").once

			for {
				i <- 1 to 5
				c <- List("A", "B", "C")
			} md.sendMethodEntry(c)

		}
	}
}
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

package com.secdec.bytefrog.hq.protocol.test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalamock.scalatest.MockFactory

import com.secdec.bytefrog.common.message.AgentOperationMode
import com.secdec.bytefrog.common.message.MessageProtocolV1
import com.secdec.bytefrog.hq.protocol.ControlMessage._
import com.secdec.bytefrog.hq.protocol.ControlMessageReaderV1

class ControlMessageReaderV1Spec extends FunSpec with ShouldMatchers with MockFactory {

	def makeInput(body: DataOutputStream => Unit): DataInputStream = {
		val baos = new ByteArrayOutputStream
		val out = new DataOutputStream(baos)
		body(out)
		val bytes = baos.toByteArray
		val bais = new ByteArrayInputStream(bytes)
		new DataInputStream(bais)
	}

	val protocol = new MessageProtocolV1
	def newReader = new ControlMessageReaderV1

	describe("ControlMessageReader, Version 1") {

		it("Should identify Error messages") {
			val reader = newReader
			val input = makeInput { out =>
				protocol.writeError(out, "Error Message")
			}
			reader.readMessage(input) shouldBe Error("Error Message")
		}

		it("Should identify Heartbeat messages, for all possible AgentOperationModes") {
			for (opMode <- AgentOperationMode.values) {
				val reader = newReader
				val input = makeInput { out =>
					protocol.writeHeartbeat(out, opMode, 3)
				}
				reader.readMessage(input) shouldBe Heartbeat(opMode, 3)
			}
		}

		it("Should identify ClassTransformed messages") {
			val reader = newReader
			val input = makeInput { out =>
				protocol.writeClassTransformed(out, "com/foo/Bar")
			}
			reader.readMessage(input) shouldBe ClassTransformed("com/foo/Bar")
		}

		it("Should identify ClassTransformFailed messages") {
			val reader = newReader
			val input = makeInput { out =>
				protocol.writeClassTransformFailed(out, "org/edu/Com")
			}
			reader.readMessage(input) shouldBe ClassTransformFailed("org/edu/Com")
		}

		it("Should identify ClassIgnored messages") {
			val reader = newReader
			val input = makeInput { out =>
				protocol.writeClassIgnored(out, "foo/bar/Baz")
			}
			reader.readMessage(input) shouldBe ClassIgnored("foo/bar/Baz")
		}

		it("Should be able to read several messages in a row without problems") {
			val reader = newReader
			val input = makeInput { out =>
				protocol.writeError(out, "Error Message")
				protocol.writeHeartbeat(out, AgentOperationMode.Paused, 3)
				protocol.writeClassTransformed(out, "ClassA")
				protocol.writeClassTransformFailed(out, "ClassB")
				protocol.writeClassIgnored(out, "ClassC")
			}
			Iterator.continually { reader.readMessage(input) }.take(5).toList shouldBe List(
				Error("Error Message"),
				Heartbeat(AgentOperationMode.Paused, 3),
				ClassTransformed("ClassA"),
				ClassTransformFailed("ClassB"),
				ClassIgnored("ClassC"))
		}

		it("Should identify an EOF at the end of a stream") {
			val reader = newReader
			val input = makeInput { _ => () }
			reader.readMessage(input) shouldBe EOF
		}

	}
}
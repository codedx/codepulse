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

package com.secdec.bytefrog.agent.util

import scala.language.postfixOps

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.Conductors
import org.scalatest.time.SpanSugar._
import org.scalatest.Suite
import org.scalatest.SuiteMixin

trait ControlSimulation extends SuiteMixin with Suite with Conductors {
	def simulateHqWriteToAgent(hqSide: (DataOutputStream) => Unit)(agentSide: (DataInputStream) => Unit): Unit = {
		val bytesOut = new ByteArrayOutputStream
		val dataOut = new DataOutputStream(bytesOut)
		hqSide(dataOut)
		dataOut.flush

		val bytesIn = new ByteArrayInputStream(bytesOut.toByteArray)
		val dataIn = new DataInputStream(bytesIn)
		agentSide(dataIn)
	}

	def simulateControlCommunication(hqSide: (DataInputStream, DataOutputStream) => Unit)(agentSide: (DataInputStream, DataOutputStream) => Unit) {
		val conductor = new Conductor
		import conductor._

		val hqOut = new PipedOutputStream
		val agentOut = new PipedOutputStream

		// using 10KB buffers since config messages can be a few KB
		val agentIn = new PipedInputStream(hqOut, 10 * 1024)
		val hqIn = new PipedInputStream(agentOut, 10 * 1024)

		thread("HQ") {
			val in = new DataInputStream(hqIn)
			val out = new DataOutputStream(hqOut)
			hqSide(in, out)
		}

		thread("Agent") {
			val in = new DataInputStream(agentIn)
			val out = new DataOutputStream(agentOut)
			agentSide(in, out)
		}

		conduct(Timeout(10 seconds))
	}
}
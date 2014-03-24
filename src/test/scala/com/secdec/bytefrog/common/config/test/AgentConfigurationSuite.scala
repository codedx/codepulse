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

package com.secdec.bytefrog.common.config.test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.collection.JavaConversions._

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import com.secdec.bytefrog.common.config.RuntimeAgentConfigurationV1

class AgentConfigurationSuite extends FunSpec with ShouldMatchers {

	describe("AgentConfiguration") {
		it("should be serializable in both directions") {
			//set up inclusions and exclusions
			val exc = List("hi", "lo")
			val inc = List("stop", "go")
			val excJ = new java.util.LinkedList[String]
			for (s <- exc) excJ add s
			val incJ = new java.util.LinkedList[String]
			for (s <- inc) incJ add s

			val config = new RuntimeAgentConfigurationV1(
				10, //runId 
				1000, //heartbeatInterval,
				excJ, //exclusions
				incJ, // inclusions
				10 * 1024 * 1024, // bufferMemoryBudget
				5, // queueRetryCount
				2 // numDataSenders
				)

			val bos = new ByteArrayOutputStream
			val out = new ObjectOutputStream(bos)
			out.writeObject(config)

			out.flush
			val bis = new ByteArrayInputStream(bos.toByteArray)
			val in = new ObjectInputStream(bis)

			val readConfig = in.readObject.asInstanceOf[RuntimeAgentConfigurationV1]

			readConfig should have(
				'runId(10),
				'heartbeatInterval(1000),
				'bufferMemoryBudget(10 * 1024 * 1024),
				'queueRetryCount(5),
				'numDataSenders(2))

			readConfig.getExclusions.toList should equal(exc)
			readConfig.getInclusions.toList should equal(inc)

		}
	}

}
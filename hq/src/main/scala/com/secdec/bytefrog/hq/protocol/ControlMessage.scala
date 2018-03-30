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

import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import javax.json.bind.spi.JsonbProvider

import com.codedx.codepulse.agent.common.config.RuntimeAgentConfigurationV1
import com.codedx.codepulse.agent.common.message.AgentOperationMode

/** Common base trait for objects/classes that represent a "control" message.
  * Control messages are ones that are sent between HQ and the Agent; essentially
  * any message that doesn't represent a traced event in some way.
  */
sealed trait ControlMessage

/** An object that contains the [[ControlMessage]] implementations.
  * General usage of this object will be to `import ControlMessage._`
  * before interacting with a [[ControlMessageSender]].
  */
object ControlMessage {
	case class Error(errorMessage: String) extends ControlMessage
	case class Heartbeat(operationMode: AgentOperationMode, sendQueueSize: Integer) extends ControlMessage

	case class ClassTransformed(className: String) extends ControlMessage
	case class ClassTransformFailed(className: String) extends ControlMessage
	case class ClassIgnored(className: String) extends ControlMessage

	case class DataBreak(sequenceId: Int) extends ControlMessage

	case object DataHelloReply extends ControlMessage

	case object Start extends ControlMessage
	case object Stop extends ControlMessage
	case object Pause extends ControlMessage
	case object Unpause extends ControlMessage
	case object Suspend extends ControlMessage
	case object Unsuspend extends ControlMessage

	case object Unknown extends ControlMessage
	case object EOF extends ControlMessage

	/** Since we designed to potentially have multiple Configuration types in the long run,
	  * the common point between different versions is that they will be serialized when sent
		* across the wire (to the Agent, from HQ). This trait makes that requirement explicit,
		* and an instance of this trait will be required in order to create a [[Configuration]]
		* message as part of the [[ControlMessageSender]] API.
	  *
	  * @tparam A The type of the Configuration object, e.g. `RuntimeAgentConfigurationV1`
	  */
	trait ConfigurationSerializer[A] {
		/** Convert `config` to a JSON string */
		def serializeJson(config: A): String
		def serializeByteArray(config: A): Array[Byte]
	}

	/** Represents an Agent Configuration, which needs to be serialized before
	  * being sent to the Agent. Since future versions may not conform to a common API, instead
	  * of attempting to create a common `AgentConfiguration` interface, we simply rely on a
	  * `serializer` to transform the `config` parameter into a serialized version.
	  */
	case class Configuration[A](config: A)(implicit val serializer: ConfigurationSerializer[A]) extends ControlMessage {
		def toJson = serializer.serializeJson(config)
		def toByteArray = serializer.serializeByteArray(config);
	}

	/** A ConfigurationSerializer implementation that works on instances of [[RuntimeAgentConfigurationV1]].
	  * This object will work as the implicit `serializer` parameter when creating a
	  * `Configuration(new RuntimeAgentConfigurationV1(...))` instance.
	  */
	implicit object RuntimeAgentConfigurationV1Serializer extends ConfigurationSerializer[RuntimeAgentConfigurationV1] {
		def serializeJson(config: RuntimeAgentConfigurationV1) = JsonbProvider.provider.create.build.toJson(config)
		def serializeByteArray(config: RuntimeAgentConfigurationV1): Array[Byte] = {
			// use Java's built-in serialization:
			// write the object to a ByteArrayOutputStream
			val buffer = new ByteArrayOutputStream
			val out = new ObjectOutputStream(buffer)
			out.writeObject(config)
			// get the resulting bytes
			buffer.toByteArray
		}
	}
}
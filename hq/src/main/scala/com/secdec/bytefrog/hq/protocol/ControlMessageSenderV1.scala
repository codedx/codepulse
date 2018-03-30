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

import java.io.DataOutputStream

import com.codedx.codepulse.agent.common.message.{MessageProtocol, MessageProtocolV1}
import com.codedx.codepulse.hq.protocol.ControlMessage.Configuration

/** A convenient singleton instance of the `ControlMessageSenderV1` class.
  * Using this object will help avoid creating new instances of the class
  * that would otherwise be needed.
  */
object ControlMessageSenderV1 extends ControlMessageSenderV1

/** A [[ControlMessageSender]] implementation that uses MessageProtocol version 1
  * to send messages.
  */
class ControlMessageSenderV1 extends ControlMessageSenderBase {

	var protocol: MessageProtocol = new MessageProtocolV1

	def writeConfigurationMessage(out: DataOutputStream, cfg: Configuration[_]) { protocol.writeConfiguration(out, cfg.toByteArray) }
}
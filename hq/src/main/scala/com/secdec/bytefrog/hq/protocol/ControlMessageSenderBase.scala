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

import com.codedx.codepulse.agent.common.message.MessageProtocol
import com.codedx.codepulse.hq.protocol.ControlMessage.{ClassIgnored, ClassTransformFailed, ClassTransformed, Configuration, DataBreak, DataHelloReply, EOF, Error, Heartbeat, Pause, Start, Stop, Suspend, Unknown, Unpause, Unsuspend}

/** A [[ControlMessageSender]] implementation that uses MessageProtocol version 1
  * to send messages.
  */
abstract class ControlMessageSenderBase extends ControlMessageSender {

  protected var protocol: MessageProtocol

  def writeConfigurationMessage(out: DataOutputStream, cfg: Configuration[_])

  def writeMessage(out: DataOutputStream, message: ControlMessage): Unit = message match {
    //write an error
    case Error(msg) => protocol.writeError(out, msg)

    //yes, Heartbeat won't be written from HQ, but the compiler will make sure that
    //we implement a case for every possible ControlMessage, so I'm implementing it anyway.
    case Heartbeat(opMode, qSize) => protocol.writeHeartbeat(out, opMode, qSize)

    // keeping the compiler happy, but this should never be called in practice
    case ClassTransformed(name) => protocol.writeClassTransformed(out, name)

    // keeping the compiler happy, but this should never be called in practice
    case ClassIgnored(name) => protocol.writeClassIgnored(out, name)

    // keeping the compiler happy, but this should never be called in practice
    case ClassTransformFailed(name) => protocol.writeClassTransformFailed(out, name)

    // keeping the compiler happy, but this should never be called in practice
    case DataBreak(seq) => protocol.writeDataBreak(out, seq)

    case DataHelloReply => protocol.writeDataHelloReply(out)

    //start and stop messages...
    case Start => protocol.writeStart(out)
    case Stop => protocol.writeStop(out)

    //pause and unpause messages...
    case Pause => protocol.writePause(out)
    case Unpause => protocol.writeUnpause(out)

    //suspend and unsuspend messages...
    case Suspend => protocol.writeSuspend(out)
    case Unsuspend => protocol.writeUnsuspend(out)

    //note: `Configuration` has a type parameter `A`, but we only care about writing
    // a configuration message here, so no need to worry about the `A`.
    case cfg: Configuration[_] => writeConfigurationMessage(out, cfg)

    //handle EOF by closing the `out`
    case EOF => out.close

    //don't try to write an Unknown
    case Unknown => throw new IllegalArgumentException("Don't write an `Unknown` message on purpose")
  }

}
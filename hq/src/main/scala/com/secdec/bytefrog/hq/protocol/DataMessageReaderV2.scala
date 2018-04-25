/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */

package com.secdec.bytefrog.hq.protocol

import java.io.{DataInputStream, IOException}

import com.codedx.codepulse.agent.common.message.MessageConstantsV3
import com.codedx.codepulse.hq.protocol.{DataMessage, DataMessageContent, DataMessageReaderV1}
import com.codedx.codepulse.hq.protocol.IO.{Data, Error, Input}

object DataMessageReaderV2 extends DataMessageReaderV2

class DataMessageReaderV2 extends DataMessageReaderV1{
  override protected def readOtherMessageType(typeId: Byte, stream: DataInputStream): Input[DataMessage] = {
    typeId match {
      case MessageConstantsV3.MsgMapSourceLocation => Data { readMapSourceLocation(stream) }
      case MessageConstantsV3.MsgMethodVisit => Data { readMethodVisit(stream) }
      case _ => Error {
        new IOException(s"Unexpected message type id: $typeId")
      }
    }
  }

  protected def readMapSourceLocation(stream: DataInputStream) = {
    //[4 bytes: source location ID]
    val sourceLocationId = stream.readInt

    //[4 bytes: assigned signature ID]
    val methodId = stream.readInt

    //[4 bytes: line]
    val startLine = stream.readInt

    //[4 bytes: line]
    val endLine = stream.readInt

    //[2 bytes: start character]
    val startCharacter = stream.readShort

    //[2 bytes: end character]
    val endCharacter = stream.readShort

    DataMessage.UnsequencedData(
      DataMessageContent.MapSourceLocation(methodId,
        startLine, endLine, startCharacter, endCharacter, sourceLocationId))
  }

  protected def readMethodVisit(stream: DataInputStream) = {
    //[4 bytes: relative timestamp]
    val timestamp = stream.readInt

    //[4 bytes: current sequence]
    val sequenceId = stream.readInt

    //[4 bytes: method signature ID]
    val methodId = stream.readInt

    //[4 bytes: source location ID]
    val sourceLocationId = stream.readInt

    //[2 bytes: thread ID]
    val threadId = stream.readUnsignedShort

    DataMessage.SequencedData(
      timestamp, sequenceId,
      DataMessageContent.MethodVisit(methodId, sourceLocationId, timestamp, threadId))
  }
}

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

import java.io.{DataInputStream, DataOutputStream, EOFException, IOException}
import java.net.SocketException

import com.codedx.codepulse.agent.common.message.{MessageConstantsV1, MessageConstantsV3}
import com.codedx.codepulse.hq.protocol.{DataEventReaderV1, DataEventType}

class DataEventReaderV2 extends DataEventReaderV1 {

  override def readDataEvent(from: DataInputStream, to: DataOutputStream): DataEventType = {
    val event = super.readDataEvent(from, to)
    if (event != DataEventType.Unknown) {
      return event
    }

    try {
      //attempt to read one of the data message types
      buffer(0) match {
        case MessageConstantsV3.MsgMapSourceLocation =>
          //[4 bytes: source location ID][4 bytes: sig ID][4 bytes: startLine][4 bytes: endLine][2 bytes: start-character][2 bytes: end-character]
          copyBytes(20, from, to)
          DataEventType.MapSourceLocation
        case MessageConstantsV3.MsgMethodVisit =>
          //[4 bytes: timestamp][4 bytes: current sequence][4 bytes: method id][4 bytes: source location id][2 bytes: thread ID]
          copyBytes(18, from, to)
          DataEventType.MethodVisit
        case x =>
          //Wrong message or Bad data
          DataEventType.Unknown
      }

    } catch {
      case e: EOFException => DataEventType.EOF
      case e: IOException if e.getMessage.toLowerCase == "stream closed" => DataEventType.EOF
      case e: SocketException if e.getMessage.toLowerCase == "connection reset" => DataEventType.EOF
      case e: IOException => DataEventType.Unknown
    }
  }
}

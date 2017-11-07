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

package com.secdec.bytefrog.hq.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import scala.annotation.tailrec

import com.secdec.bytefrog.common.message.MessageConstantsV1

class DataEventReaderV1 extends DataEventReader {

	private val bufferSize = 1024
	private val buffer = new Array[Byte](bufferSize)

	//helper
	@tailrec private def copyBytes(n: Int, from: DataInputStream, to: DataOutputStream): Unit = {
		if (n > 0) {
			val num = n % bufferSize
			from.readFully(buffer, 0, num)
			to.write(buffer, 0, num)

			//this recursion will be turned into a loop by the compiler, thanks to @tailrec
			copyBytes(n - num, from, to)
		}
	}

	//helper
	private def copyUTF(from: DataInputStream, to: DataOutputStream) = {
		val utfLen = from.readUnsignedShort
		to.writeShort(utfLen)

		copyBytes(utfLen, from, to)
	}

	/** Reads the bytes for an individual data event, and copies them to the `to` stream,
	  * returning the type of event that was read.
	  *
	  * NOTE: this method is *not* thread safe. It uses a buffer that belongs to the reader
	  * instance, so multiple threads calling this method on the same reader instance will
	  * likely corrupt the data.
	  */
	def readDataEvent(from: DataInputStream, to: DataOutputStream): DataEventType = {

		try {

			//read 1 byte: [type ID]
			copyBytes(1, from, to)

			//attempt to read one of the 5 data message types
			buffer(0) match {
				case MessageConstantsV1.MsgMapThreadName =>
					//[2 bytes: thread ID][4 bytes: rel timestamp][2 bytes: string length][n bytes: String]
					copyBytes(6, from, to)
					copyUTF(from, to)
					DataEventType.MapThreadName
				case MessageConstantsV1.MsgMapMethodSignature =>
					//[4 bytes: sig ID][2 bytes: string length][n bytes: String]
					copyBytes(4, from, to)
					copyUTF(from, to)
					DataEventType.MapMethodName
				case MessageConstantsV1.MsgMethodEntry =>
					//[4 bytes: timestamp][4 bytes: current sequence][4 bytes: method id][2 bytes: thread ID]
					copyBytes(14, from, to)
					DataEventType.MethodEntry
				case MessageConstantsV1.MsgMethodExit =>
					//[4 bytes: timestamp][4 bytes: current sequence][4 bytes: method ID][2 bytes: line num][2 bytes: thread ID]
					copyBytes(16, from, to)
					DataEventType.MethodExit
				case MessageConstantsV1.MsgException =>
					//[4 bytes: timestamp][4 bytes: current sequence][4 bytes: method ID][2 bytes: string length][n bytes: String][2 bytes: line num][2 bytes: thread ID]
					copyBytes(12, from, to)
					copyUTF(from, to)
					copyBytes(4, from, to)
					DataEventType.ExceptionEvent
				case MessageConstantsV1.MsgExceptionBubble =>
					//[4 bytes: relative timestamp][4 bytes: current sequence][4 bytes: method signature ID][2 bytes: thread ID]
					copyBytes(14, from, to)
					DataEventType.ExceptionBubbleEvent
				case MessageConstantsV1.MsgMarker =>
					//[4 bytes: timestamp][4 bytes: seq][utf: key][utf: value]
					copyBytes(8, from, to)
					copyUTF(from, to)
					copyUTF(from, to)
					DataEventType.Marker
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
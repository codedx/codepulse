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

/** Base trait for classes that represent data received from an Agent
  */
sealed trait DataMessage {
	def content: DataMessageContent
}

object DataMessage {
	case class SequencedData(timestamp: Int, sequence: Int, val content: DataMessageContent) extends DataMessage
	case class UnsequencedData(val content: DataMessageContent) extends DataMessage
}

/** Base trait for classes that represent actual trace data
  */
sealed trait DataMessageContent

/** Container for the various concrete `DataMessageContent` classes.
  * Each DataMessage class is POJO representation of the messages
  * described by the MessageProtocol document, minus any sequencing info.
  */
object DataMessageContent {
	case class MapThreadName(
		threadName: String,
		threadId: Int,
		timestamp: Int)
		extends DataMessageContent

	case class MapMethodSignature(
		methodSig: String,
		methodId: Int)
		extends DataMessageContent

	case class MapException(
		exception: String,
		exceptionId: Int)
		extends DataMessageContent

	case class MethodEntry(
		methodId: Int,
		timestamp: Int,
		threadId: Int)
		extends DataMessageContent

	case class MethodExit(
		methodId: Int,
		timestamp: Int,
		lineNum: Int,
		threadId: Int)
		extends DataMessageContent

	case class Exception(
		exceptionId: Int,
		methodId: Int,
		timestamp: Int,
		lineNum: Int,
		threadId: Int)
		extends DataMessageContent

	case class ExceptionBubble(
		exceptionId: Int,
		methodId: Int,
		timestamp: Int,
		threadId: Int)
		extends DataMessageContent

	case class Marker(
		key: String,
		value: String,
		timestamp: Int)
		extends DataMessageContent
}
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

	case class MapSourceLocation(
		methodId: Int,
		startLine: Int,
		endLine: Int,
		startCharacter: Short,
		endCharacter: Short,
		sourceLocationId: Int)
		extends DataMessageContent

	case class SourceLocationCount(
		methodId: Int,
		sourceLocationCount: Int)
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

	case class MethodVisit(
		methodId: Int,
		sourceLocationId: Int,
		timestamp: Int,
		threadId: Int)
		extends DataMessageContent

	case class MethodExit(
		methodId: Int,
		timestamp: Int,
		exceptionThrown: Boolean,
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
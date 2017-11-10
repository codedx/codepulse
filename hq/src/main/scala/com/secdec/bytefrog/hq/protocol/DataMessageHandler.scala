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

/** Describes a collection of callback methods to be used by a `DataMessageParser`
  * while parsing a stream of data messages.
  */
trait DataMessageHandler {

	/** This method is called by a parser when it encounters a MapThreadName message. */
	def handleMapThreadName(threadName: String, threadId: Int, timestamp: Int): Unit

	/** This method is called by a parser when it encounters a MapMethodSignature message */
	def handleMapMethodSignature(methodSig: String, methodId: Int): Unit

	/** This method is called by a parser when it encounters a MapException message */
	def handleMapException(exception: String, exceptionId: Int): Unit

	/** This method is called by a parser when it encounters a MethodEntry message */
	def handleMethodEntry(methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int): Unit

	/** This method is called by a parser when it encounters a MethodExit message */
	def handleMethodExit(methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int): Unit

	/** This method is called by a parser when it encounters an Exception message */
	def handleExceptionMessage(exceptionId: Int, methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int): Unit

	/** This method is called by a parser when it encounters a bubbled exception */
	def handleExceptionBubble(exceptionId: Int, methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int)

	/** This method is called by a parser when it encounters a marker message */
	def handleMarkerMessage(timestamp: Int, sequence: Int, key: String, value: String)

	/** This method is called by a parser when it encounters an error while parsing a stream */
	def handleParserError(error: Throwable): Unit

	/** This method is called by a parser when it encounters the end of a stream */
	def handleParserEOF: Unit

	/** This method is called by a parser when it encounters a data break, if they're told to process breaks */
	def handleDataBreak: Unit
}

/** An implementation of `DataMessageHandler` where each of the required
  * methods are implemented as a no-op.
  */
class DefaultDataMessageHandler extends DataMessageHandler {
	def handleMapThreadName(threadName: String, threadId: Int, timestamp: Int) = ()
	def handleMapMethodSignature(methodSig: String, methodId: Int) = ()
	def handleMapException(exception: String, exceptionId: Int) = ()

	def handleMethodEntry(methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int) = ()
	def handleMethodExit(methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int) = ()

	def handleExceptionMessage(exceptionId: Int, methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int) = ()
	def handleExceptionBubble(exceptionId: Int, methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int) = ()

	def handleMarkerMessage(timestamp: Int, sequence: Int, key: String, value: String) = ()

	def handleParserError(error: Throwable) = ()
	def handleParserEOF = ()

	def handleDataBreak = ()
}
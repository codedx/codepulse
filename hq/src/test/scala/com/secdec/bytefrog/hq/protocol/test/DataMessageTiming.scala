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

package com.secdec.bytefrog.hq.protocol.test

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.FileInputStream

import com.secdec.bytefrog.hq.protocol.DataMessage
import com.secdec.bytefrog.hq.protocol.DataMessageContent
import com.secdec.bytefrog.hq.protocol.DataMessageReaderV1
import com.secdec.bytefrog.hq.protocol.DataMessageParserV1
import com.secdec.bytefrog.hq.protocol.DefaultDataMessageHandler
import com.secdec.bytefrog.hq.protocol.IO

object DataMessageTiming {

	def time[T](body: => T): (Long, T) = {
		val start = System.currentTimeMillis
		val result = body
		val end = System.currentTimeMillis
		(end - start) -> result
	}

	def withDataStream[T](path: String)(body: DataInputStream => T): T = {
		val fin = new FileInputStream(path)
		val bin = new BufferedInputStream(fin)
		val din = new DataInputStream(bin)
		try {
			body(din)
		} finally {
			din.close
			bin.close
			fin.close
		}
	}

	class Counter(val name: String, init: Int = 0) {
		private var c = init
		def inc = { c += 1; this }
		def apply() = c
	}

	class Counts {
		val methodEntries = new Counter("method entries")
		val methodExits = new Counter("method extis")
		val mappedThreads = new Counter("mapped threads")
		val mappedMethods = new Counter("mapped methods")
		val mappedExceptions = new Counter("mapped exceptions")
		val exceptions = new Counter("exceptions")
		val exceptionBubbles = new Counter("exception bubbles")
		val markers = new Counter("markers")

		def counters = List(methodEntries, methodExits, mappedThreads, mappedMethods, exceptions, exceptionBubbles, markers)

		def printStats = {
			val total = counters.map(_()).sum
			val lines = counters.map { c => s"\t${c()} ${c.name}" }
			println(s"$total total events")
			lines foreach println
		}
	}

	def pullParserTest(data: DataInputStream): Counts = {
		val reader = DataMessageReaderV1
		val c = new Counts
		val itr = Iterator.continually { reader.readMessage(data) } takeWhile {
			case IO.Data(_) => true
			case _ => false
		}
		itr foreach {
			case IO.Data(e) => e.content match {
				case _: DataMessageContent.Exception => c.exceptions.inc
				case _: DataMessageContent.ExceptionBubble => c.exceptionBubbles.inc
				case _: DataMessageContent.MethodEntry => c.methodEntries.inc
				case _: DataMessageContent.MethodExit => c.methodExits.inc
				case _: DataMessageContent.MapThreadName => c.mappedThreads.inc
				case _: DataMessageContent.MapMethodSignature => c.mappedMethods.inc
				case _: DataMessageContent.MapException => c.mappedExceptions.inc
				case _: DataMessageContent.Marker => c.markers.inc
			}

			case _ =>
		}
		c
	}

	def pushParserTest(data: DataInputStream): Counts = {
		val c = new Counts
		val handler = new DefaultDataMessageHandler {
			override def handleMapThreadName(threadName: String, threadId: Int, timestamp: Int) = c.mappedThreads.inc
			override def handleMapMethodSignature(methodSig: String, methodId: Int) = c.mappedMethods.inc
			override def handleMapException(exc: String, excId: Int) = c.mappedExceptions.inc
			override def handleMethodEntry(methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int) = c.methodEntries.inc
			override def handleMethodExit(methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int) = c.methodExits.inc
			override def handleExceptionMessage(exception: Int, methodId: Int, timestamp: Int, sequenceId: Int, lineNum: Int, threadId: Int) =
				c.exceptions.inc
			override def handleExceptionBubble(exception: Int, methodId: Int, timestamp: Int, sequenceId: Int, threadId: Int) = c.exceptionBubbles.inc

			override def handleParserError(error: Throwable) = throw error
			override def handleParserEOF = ()
		}
		val parser = DataMessageParserV1
		parser.parse(data, handler)
		c
	}

	def main(args: Array[String]): Unit = {

		if (args.size < 1) {
			println("Usage: DataMessageTiming <data-dump-file>")
		} else {
			val path = args(0)

			println("Running 'PULL' api test...")
			val (t1, c1) = time {
				withDataStream(path) { pullParserTest }
			}

			println(s"PULL api test completed in $t1 ms")
			c1.printStats

			println
			println("Running 'PUSH' api test...")
			val (t2, c2) = time {
				withDataStream(path) { pushParserTest }
			}

			println(s"PUSH api test completed in $t2 ms")
			c2.printStats
		}
	}
}
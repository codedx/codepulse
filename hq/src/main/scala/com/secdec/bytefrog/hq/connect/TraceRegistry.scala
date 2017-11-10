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

package com.codedx.codepulse.hq.connect

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.ref.WeakReference

import com.codedx.codepulse.agent.common.config.RuntimeAgentConfigurationV1
import com.codedx.codepulse.hq.trace.Trace
import com.codedx.codepulse.hq.util.WeakMap

object TraceRegistry extends TraceRegistry

/** An object that remembers which TraceObjects are running (by calling `registerTrace` and
  * `unregisterTrace`), and using that information to correctly assign incoming Data Connections.
  */
trait TraceRegistry {

	private val runningTraces = new WeakMap[Byte, Trace]()
	private val waitingTraceFutures: collection.concurrent.Map[Byte, Promise[Trace]] =
		new collection.concurrent.TrieMap

	// iterates [0, 1, 2, ..., 255, 0, 1, 2 ... ] repeatedly
	// used by `freshRunId`
	private val idGenerator = Iterator.iterate(0) {
		case 255 => 0
		case x => x + 1
	}.map(_.toByte)

	/** Tells the registry about a trace object. If there are any futures waiting
	  * for a trace with a matching runId (created by calling `getTrace(runId)`,
	  * those futures will be completed successfully with `trace`.
	  *
	  * @return `true` if the registration succeeded, or `false` if a trace was
	  * already registered with the same `runId`.
	  */
	def registerTrace(trace: Trace): Boolean = runningTraces.synchronized {
		val runId = trace.runId
		runningTraces.get(runId) match {
			//in case of an existing entry that still refers to something, return false
			case Some(_) => false

			//for all other cases, add a new entry and return true
			case None =>
				runningTraces += runId -> trace

				//check for a waiting future
				for (promise <- waitingTraceFutures.get(runId)) {
					//satisfy the future and remove it from the map
					promise.trySuccess(trace)
					waitingTraceFutures.remove(runId, promise)
				}
				true
		}
	}

	/** Get the trace with the given `runId`, wrapped as a `Future`. The resulting
	  * Future may be completed later on, as a new Trace is registered, or may already
	  * be satisfied if one such Trace is already registered.
	  *
	  * @return A Future representing the Trace with the given `runId`.
	  */
	def getTrace(runId: Byte): Future[Trace] = runningTraces.synchronized { runningTraces get runId } match {
		case Some(id) => Future.successful(id)
		case None =>
			val promise = Promise[Trace]
			//add the promise to the map, if there wasn't one already
			waitingTraceFutures.putIfAbsent(runId, promise) match {
				// None signifies that there wasn't one already there
				case None => promise.future

				// in the case of a Some, use the existing promise, and let
				// the new one be discarded
				case Some(olderPromise) => olderPromise.future
			}
	}

	/** Finds a byte that can be used as a `runId` for a trace, such that the returned
	  * id byte is not currently in use by any currently active (and registered) trace run.
	  *
	  * This method selects bytes in a "round-robin" fashion, also ensuring that the returned
	  * runId is not currently associated with an existing client; this way, calling
	  * `freshRunId` twice in a row will return distinct, valid ids. The only way
	  * this method should fail would be if 256 clients were registered (enough to
	  * use every valid runId value).
	  *
	  * @return `Some(runId)` if a runId is available, or `None` if every possible
	  * runId has already been registered.
	  */
	def freshRunId: Option[Byte] = runningTraces.synchronized {
		//use take(255) to ensure that we don't loop forever in the iterator.find
		idGenerator.take(255).find { !runningTraces.contains(_) }
	}
}
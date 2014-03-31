/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
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

package com.secdec.codepulse.tracer

import akka.actor._
import concurrent.duration._
import collection.mutable.ListBuffer

/** A convenience interface for `AccumulationRequestActor`
  */
class AccumulationRequestController(actorSystem: ActorSystem) {
	import AccumulationRequestActor._
	val actor = actorSystem.actorOf(Props[AccumulationRequestActor])

	override def finalize = {
		actor ! PoisonPill
		super.finalize
	}

	def makeRequest(respond: Traversable[Int] => Unit) = {
		actor ! MakeRequest(respond)
	}

	def enterData(data: Traversable[Int]) = {
		actor ! EnterData(data)
	}
}

object AccumulationRequestActor {
	case class MakeRequest(respond: Traversable[Int] => Unit)
	case class EnterData(data: Traversable[Int])
	case object NoDataTimeout
}

/** An actor that acts as a buffer that matches requests and data,
  * attempting to only satisfy requests when some data is available,
  * or after a certain timeout period.
  *
  * This is used to facilitate long polling of trace encounter accumulation data.
  * @author DylanH
  */
class AccumulationRequestActor extends Actor {
	import AccumulationRequestActor._

	val accumulatedData = new ListBuffer[Int]
	var awaitingRequest: Option[Traversable[Int] => Unit] = None
	var timeoutTask: Option[Cancellable] = None

	import context.dispatcher

	def receive = {
		case MakeRequest(respond) => {
			// If there is already a request waiting, satisfy it right now.
			// Get and clear the accumulated data, sending it to that request.
			satisfyWaitingRequest()

			// If there is no data, set up the request to be fulfilled later,
			// otherwise, fulfil the request immediately
			if (accumulatedData.isEmpty) {
				// Set the awaitingRequest to the `respond` that we just got
				awaitingRequest = Some(respond)

				// Set a new timeout task.
				val t = context.system.scheduler.scheduleOnce(10.seconds, self, NoDataTimeout)
				timeoutTask = Some(t)
			} else {
				val data = getAndClearAccumulation()
				respond(data)
			}
		}

		case EnterData(data) => {
			// Add `data` to the accumulated data.
			accumulatedData appendAll data

			// If there is an awaiting request, and the accumulated data isn't empty, satisfy it now.
			if (!accumulatedData.isEmpty) {
				satisfyWaitingRequest()
			}
		}

		case NoDataTimeout => {
			// If there is a request waiting, satisfy it with the current accumulated data
			// Clear the accumulated data.
			satisfyWaitingRequest()
		}
	}

	def satisfyWaitingRequest() = {
		for (r <- awaitingRequest) {
			r(getAndClearAccumulation())
		}
		awaitingRequest = None
		cancelTimeout()
	}
	def cancelTimeout() = {
		timeoutTask foreach { _.cancel }
		timeoutTask = None
	}
	def getAndClearAccumulation() = {
		val response = accumulatedData.result
		accumulatedData.clear()
		response
	}
}
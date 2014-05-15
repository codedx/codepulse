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

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Stash
import com.secdec.bytefrog.hq.trace.Trace
import net.liftweb.common.Loggable
import com.secdec.codepulse.components.notifications.Notifications
import com.secdec.codepulse.components.notifications.NotificationMessage
import com.secdec.codepulse.components.notifications.NotificationSettings
import akka.actor.Props
import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.ActorSystem
import scala.concurrent.Promise
import scala.util.Success

/** Represents a possible reaction to an incoming agent connection,
  * by either the user or the system.
  */
sealed trait TraceConnectionAcknowledgment
object TraceConnectionAcknowledgment {

	/** The user acknowledged the connection, intending to trace with the given `target` */
	case class Acknowledged(target: TracingTarget) extends TraceConnectionAcknowledgment

	/** The user rejected the connection, so it should be dropped */
	case object Rejected extends TraceConnectionAcknowledgment

	/** The system stopped caring about the connection, probably because
	  * it dropped itself.
	  */
	case object Canceled extends TraceConnectionAcknowledgment
}

/** An object that manages the acceptance and rejection of traces. Instances of
  * this trait will generally have two clients; one that requests acknowledgment
  * or traces (and potentially cancels its request for acknowledgment); and another
  * that accepts or rejects traces.
  *
  * Generally, a `TraceConnectionAcknowledger` will be in one of two states;
  *
  * In the first state, it is idle. It is not yet looking for acknowledgment for any
  * trace; calling `getTraceAcknowledgment` will case it to change to the second state,
  * but calling any other method  will have no effect.
  *
  * In the second state, it is waiting for the acknowledgment of a trace. This request
  * may be fulfilled (`acknowledgeCurrentTrace`), rejected (`rejectCurrentTrace`) or
  * cancelled (`cancelCurrentAcknowledgmentRequest`).
  *
  * Client code should try to avoid queuing calls to `getTraceAcknowledgement`, as it
  * could lead to confusion or unexpected behavior with regards to canceling requests.
  */
trait TraceConnectionAcknowledger {

	/** Requests acknowledgment of the given `trace`. The acknowledgment is returned as
	  * a `Future`. The future will complete with a different value depending on which
	  * other method is called; `cancelCurrentAcknowledgmentRequest` will cause the
	  * acknowledgment future to complete as `Canceled`; `acknowledgeCurrentTrace`
	  * will cause the future to complete as `Acknowledged`, associated with a given
	  * `TracingTarget`; `rejectCurrentTrace` will cause the future to complete as `Rejected`.
	  */
	def getTraceAcknowledgment(trace: Trace): Future[TraceConnectionAcknowledgment]

	/** If there is currently a request waiting for acknowledgment of a trace,
	  * its future will complete as `Canceled`. If not, this method has no effect.
	  */
	def cancelCurrentAcknowledgmentRequest()

	/** If there is currently a request waiting for acknowledgment of a trace,
	  * its future will complete with `Acknowledged(target)`. If not, this method
	  * has no effect.
	  */
	def acknowledgeCurrentTrace(target: TracingTarget)

	/** If there is currently a request waiting for acknowledgment of a trace,
	  * its future will complete as `Rejected`. If not, this method has no effect.
	  */
	def rejectCurrentTrace()
}

/** Actor companion object that provides a concrete implementation of the
  * `TraceConnectionAcknowledger` trait by using `TraceConnectionAcknowledgerActor`s
  * under the hood.
  */
object TraceConnectionAcknowledgerActor {

	/** A client wants to know if this `trace` will be acknowledged.
	  * The actor should fulfil the `ackPromise` once the user accepts
	  * or rejects the trace.
	  */
	case class RequestAcknowledgment(trace: Trace, ackPromise: Promise[TraceConnectionAcknowledgment])

	case object CancelCurrentAcknowledgementRequest

	/** The user wants to connect the waiting trace to this `target` */
	case class UserAcknowledgement(target: TracingTarget)

	/** The user wants to reject the waiting trace connection */
	case object UserReject

	def props = Props[TraceConnectionAcknowledgerActor]

	/** A concrete implementation of TraceConnectionAcknowledger that uses a
	  * TraceConnectionAcknowledger's `ActorRef` and message passing under
	  * the hood.
	  */
	case class API(acknowledgerActor: ActorRef) extends TraceConnectionAcknowledger {

		def getTraceAcknowledgment(trace: Trace): Future[TraceConnectionAcknowledgment] = {
			val ackPromise = Promise[TraceConnectionAcknowledgment]
			acknowledgerActor ! RequestAcknowledgment(trace, ackPromise)
			ackPromise.future
		}

		def cancelCurrentAcknowledgmentRequest() = {
			acknowledgerActor ! CancelCurrentAcknowledgementRequest
		}

		def acknowledgeCurrentTrace(target: TracingTarget) = {
			acknowledgerActor ! UserAcknowledgement(target)
		}

		def rejectCurrentTrace() = {
			acknowledgerActor ! UserReject
		}
	}

	/** Convenience creator for an API instance */
	def create(actorSystem: ActorSystem): API = {
		val acknowledgerActor = actorSystem.actorOf(props)
		API(acknowledgerActor)
	}
}

/** An Akka actor that acts as an implementation of a`TraceConnectionAcknowledger`. An actual
  * instance of the `TraceConnectionAcknowledger` trait can be obtained via this class's
  * companion object.
  */
class TraceConnectionAcknowledgerActor extends Actor with Stash with Loggable {
	import TraceConnectionAcknowledgerActor._
	import context.dispatcher

	/** Default state, where requests for acknowledgement cause it to change to the
	  * 'waiting' state (via `waitForUserAck`), and all other messages are ignored.
	  */
	def receive = {
		case RequestAcknowledgment(trace, ackPromise) => waitForUserAck(trace, ackPromise)

		case msg @ (CancelCurrentAcknowledgementRequest | UserAcknowledgement(_) | UserReject) =>
			logger.debug(s"Got `$msg`, but since there is no current request for trace acknowledgment, it will be ignored")
	}

	/** Changes state so that user acknowledgment messages will fulfill the `ackPromise`
	  * in relation to the given `trace`. New requests for acknowledgment will be stashed
	  * until the state changes back to normal (which will happen once the promise is
	  * completed).
	  */
	def waitForUserAck(trace: Trace, ackPromise: Promise[TraceConnectionAcknowledgment]) = {

		/*
		 * NOTE: if the timing is (un)lucky, the ackPromise might be completed
		 * multiple times. Because of this, we use `trySuccess` and `tryFailure`
		 * so that we don't cause issues by completing a promise twice, and so
		 * that only the block that gets there first will execute.
		 */

		context.become({

			case UserAcknowledgement(target) =>
				if (ackPromise trySuccess TraceConnectionAcknowledgment.Acknowledged(target)) {
					logger.info(s"User acknowledged trace. Connected to ${target}")
				}

			case CancelCurrentAcknowledgementRequest =>
				if (ackPromise trySuccess TraceConnectionAcknowledgment.Canceled) {
					logger.info("Acknowledgement was cancelled")
				}

			case UserReject =>
				if (ackPromise trySuccess TraceConnectionAcknowledgment.Rejected) {
					logger.info("User rejected trace.")
				}

			case RequestAcknowledgment(_, _) =>
				// stash this so we can handle it when we get back to a non-waiting state
				logger.debug("Got acknowledgement request while already waiting for one. Sashing this request for later.")
				stash()

		}, false)

		// once the promise is fulfilled, go back to the default state
		// and unstash any requests that came in while we were waiting
		ackPromise.future.onComplete { triedResult =>
			unstashAll()
			context.unbecome()
		}

	}

}
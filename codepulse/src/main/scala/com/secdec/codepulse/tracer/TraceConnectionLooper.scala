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

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import com.codedx.codepulse.hq.data.processing.DataRouter
import com.codedx.codepulse.hq.trace.DefaultSegmentAccess
import com.codedx.codepulse.hq.trace.Trace
import com.codedx.codepulse.hq.trace.TraceDataManager
import com.codedx.codepulse.hq.trace.TraceEndReason
import com.codedx.codepulse.hq.trace.TraceSegmentManager
import com.secdec.codepulse.components.notifications.NotificationMessage
import com.secdec.codepulse.components.notifications.NotificationSettings
import com.secdec.codepulse.components.notifications.Notifications

import TraceConnectionAcknowledgment.Acknowledged
import TraceConnectionAcknowledgment.Canceled
import TraceConnectionAcknowledgment.Rejected
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import net.liftweb.common.Loggable
import reactive.EventSource
import reactive.EventStream
import reactive.Observing

object TraceConnectionLooper {

	sealed trait State
	case object Idle extends State
	case object Connecting extends State
	case class Running(target: TracingTarget) extends State

	// message sent to get the current actor's state
	private case object RequestState

	// message sent to watch the actor's state changes
	private case class WatchStateChanges(sub: EventStream[State] => Unit)

	// messages for when an agent connects or not
	private case class TraceConnected(counter: Int, trace: Trace)
	private case class NoTraceConnected(counter: Int)

	private case object TraceStarted
	private case object TraceFinished

	// message to trigger a request for trace acknowledgement
	private case class RequestTraceAck(trace: Trace)

	// message for when the agent connection has been acknowledged
	private case class TraceAcknowledged(counter: Int, ack: TraceConnectionAcknowledgment)

	// message to trigger the search for a new tracer agent connection
	private case object RequestNewTrace

	// messages to start/stop the loop
	private case object Stop
	private case object Start

	def props(acknowledger: TraceConnectionAcknowledger) =
		Props(classOf[TraceConnectionLooper], acknowledger)

	case class API(loopActor: ActorRef) {
		def start() = loopActor ! Start
		def stop() = loopActor ! Stop

		def getState = {
			implicit val timeout = Timeout(5.seconds)
			(loopActor ? RequestState).mapTo[State]
		}

		def watchStateChanges(sub: EventStream[State] => Unit) = {
			loopActor ! WatchStateChanges(sub)
		}
	}

	def create(actorSystem: ActorSystem, acknowledger: TraceConnectionAcknowledger): API = {
		val loopActor = actorSystem.actorOf(props(acknowledger))
		API(loopActor)
	}
}

/** An Actor that continuously watches for new trace connections via the `TraceServer`.
  * When it receives a new connection (as a `Trace` instance), it sends it to the
  * `acknowledgeTrace` function whose job it is to handle or reject the new connection.
  * The loop may be started or stopped at any time; when initialized, the loop is stopped.
  * Any `Trace` that goes unacknowledged (when `acknowledgeTrace` finishes with a Failure
  * or `false`) will be killed; an acknowledged trace is expected to become handled by the
  * acknowledger.
  *
  * Use the companion object's methods to get an instance of the `API` class, which can be
  * used to interact with an `ActorRef` for this class.
  */
class TraceConnectionLooper(acknowledger: TraceConnectionAcknowledger) extends Actor with Observing with Loggable {

	import TraceConnectionLooper._
	import context.dispatcher

	/** `counter` is used to identify the loop iteration number.
	  * Due to the asynchronous nature of the operations, events
	  * from previous loops might be sent when they should be
	  * ignored. Keeping a loop number helps determine which events
	  * to ignore.
	  */
	private var counter = 0

	protected val stateChanges = new EventSource[State]
	protected class StateReceive(val state: State, val receive: Receive)
	protected def StateReceive(state: State, body: Receive): StateReceive = {
		new StateReceive(state, body orElse {
			case RequestState => sender ! state
			case WatchStateChanges(sub) => sub(stateChanges)
		})
	}
	protected def becomeState(newSR: StateReceive, discardOld: Boolean = true) = {
		context.become(newSR.receive, discardOld)
		println(s"Connection Looper becoming state ${newSR.state}")
		stateChanges fire newSR.state
	}

	// initially, the looper does nothing until `Start`ed
	def receive = StateStopped.receive

	/** IDLE
	  * In this state, the actor expects a `RequestNewTrace` message
	  * in order to actually initiate a new trace request.
	  */
	val StateIdle = StateReceive(Idle, {
		case RequestNewTrace => onRequestNewTrace(counter)

		case Stop => onStop()
	})

	/** STOPPED
	  * In this state, the "loop" is not running. Any trace-related
	  * messages will be ignored, and any new connections that might
	  * have been requested earlier will be killed when they report in.
	  * To exit the "STOPPED" state, send a `Start` message.
	  */
	val StateStopped = StateReceive(Idle, {
		case TraceConnected(anyCounter, trace) => trace.kill()

		case Start => onStart()
	})

	/** WAITING FOR TRACE
	  * In this state, a trace connection request has been made but not
	  * yet fulfilled. If the request fails, the loop starts over; if it
	  * succeeds, the actor will seek acknowledgement for the trace (via
	  * the `acknowledgeTrace` constructor argument).
	  */
	def WaitingForTrace(currentCounter: Int) = StateReceive(Idle, {
		case TraceConnected(`currentCounter`, trace) => onTraceConnected(currentCounter, trace)
		case NoTraceConnected(`currentCounter`) => onStart()

		case TraceConnected(wrongCounter, trace) =>
			logger.debug(s"Trace connected with wrong counter number: $wrongCounter (expected $currentCounter)")
			trace.kill()
		case NoTraceConnected(wrongCounter) =>
			logger.debug(s"A trace failed to connect, but the counter was wrong, so we don't care. ($wrongCounter but expected $currentCounter)")

		case Stop => onStop()
	})

	/** WAITING FOR ACK
	  * In this state, a connected trace has been sent for acknowledgement.
	  * If the `acknowledgeTrace` function succeeds with a `true`, this actor
	  * assumes that the trace is now being managed by some other component,
	  * and may forget about it. Otherwise, the trace is considered "rejected"
	  * and will be killed before restarting the loop.
	  */
	def WaitingForAcknowledgement(currentCounter: Int, trace: Trace) = StateReceive(Connecting, {
		case TraceAcknowledged(`currentCounter`, ack) =>
			import TraceConnectionAcknowledgment._
			ack match {
				// if the trace was not acknowledged, start the app without tracing
				case Rejected | Canceled =>
					stopTracing(trace)
					onStart()

				// if the trace *was* acknowledged, start tracing
				case Acknowledged(target) =>
					onTraceAcknowledged(trace, target)
			}

		case TraceAcknowledged(wrongCounter, acked) =>
			// An old trace connection (that probably died) was [un-]acknowledged,
			// but the looper had started looking for a new trace, so the [un-]ack
			// should be ignored at this point.
			()

		case Stop =>
			stopTracing(trace)
			onStop()
	})

	def StateRunning(trace: Trace, target: TracingTarget) = StateReceive(Running(target), {
		case TraceFinished => onStart()
	})

	/** Turn off the trace and allow the traced app to run normally.
	  * This needs to happen when a trace is rejected, or when the looper
	  * is stopped while waiting for acknowledgement of a trace.
	  */
	def stopTracing(trace: Trace) = try {
		if (trace.isStarted) {
			trace.stop()
		} else {
			// stop sending trace events
			trace.suspendTracing()

			// trace.start requires a TraceDataManager. This one does nothing.
			// This should be redundant, as the trace would have been configured
			// to ignore all classes, and we just suspended it so that it wouldn't
			// try to send events anyway.
			val noopDataManager = new TraceDataManager {
				def setupDataProcessors(router: DataRouter): Unit = ()
				def setupSegmentProcessing(startTime: Long): TraceSegmentManager =
					new TraceSegmentManager(new DefaultSegmentAccess)
				def finish(reason: TraceEndReason, traceWasStarted: Boolean): Unit = ()
			}

			// "start" the trace to allow it to get out of pre-main
			trace.start(noopDataManager)
			// then stop it so it doesn't get in anyone's way.
			trace.stop()
			// the app should run normally at this point.
		}
	} catch {
		case e: Exception =>
			// if this happens, the trace was probably already killed off
			// and the socket connections were down. Make a note and move on.
			logger.error("Tried to stop a trace, but got an exception", e)
	}

	// called to initate a new loop iteration from the IDLE state
	def onStart() = {
		counter += 1
		becomeState(StateIdle)
		self ! RequestNewTrace
	}

	// called when a `Stop` message is received, to move to the STOPPED state
	def onStop(): Unit = {
		becomeState(StateStopped)
	}

	// called when a new trace is requested, to move to the WAITING FOR TRACE state
	def onRequestNewTrace(currentCounter: Int): Unit = {
		becomeState(WaitingForTrace(currentCounter))

		logger.debug("Requesting a new Trace connection")

		// request a new trace from the trace server
		TraceServer.awaitNewTrace() onComplete {
			case Success(trace) => self ! TraceConnected(currentCounter, trace)
			case Failure(_) => self ! NoTraceConnected(currentCounter)
		}
	}

	// called when a trace is connected, to move to the WAITING FOR ACK state
	def onTraceConnected(currentCounter: Int, trace: Trace): Unit = {
		becomeState(WaitingForAcknowledgement(currentCounter, trace))

		val acknowledgment = acknowledger.getTraceAcknowledgment(trace)

		// if the trace 'completes' before it has been acknowledged, we need to look for a new one
		// (this might happen if the agent gets ctrl+C'd before the ack)
		for {
			completion <- trace.completion
			if !acknowledgment.isCompleted
		} {
			acknowledger.cancelCurrentAcknowledgmentRequest()
			Notifications.enqueueNotification(NotificationMessage.AgentDisconnected, NotificationSettings.defaultDelayed(3000), persist = false)
			onStart()
		}

		import akka.pattern.pipe
		acknowledgment map { TraceAcknowledged(currentCounter, _) } pipeTo self
	}

	def onTraceAcknowledged(trace: Trace, target: TracingTarget): Unit = {
		becomeState(StateRunning(trace, target))

		target.connectTrace(trace) onComplete {
			case Success(_) =>
				trace.completion onComplete {
					case _ => self ! TraceFinished
				}
			case Failure(_) =>
				stopTracing(trace)
				onStart()
		}
	}

}

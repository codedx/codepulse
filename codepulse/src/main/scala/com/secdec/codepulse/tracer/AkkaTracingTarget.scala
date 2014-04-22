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

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.secdec.bytefrog.hq.trace.Trace
import com.secdec.bytefrog.hq.trace.TraceEndReason
import com.secdec.codepulse.data.jsp.JspMapper
import com.secdec.codepulse.data.trace.TraceData
import com.secdec.codepulse.data.trace.TraceId

import akka.actor._
import akka.pattern.AskSupport
import akka.util.Timeout
import reactive.EventSource
import reactive.EventStream

sealed abstract class TracingTargetState(val name: String)
object TracingTargetState {
	case object Loading extends TracingTargetState("loading")
	case object Idle extends TracingTargetState("idle")
	case object Connecting extends TracingTargetState("connecting")
	case object Running extends TracingTargetState("running")
	case object Ending extends TracingTargetState("ending")
	case object DeletePending extends TracingTargetState("delete-pending")
	case object Deleted extends TracingTargetState("deleted")
}

/** Manages the state of a single "Trace". A Trace in this context is
  * some persistent target, which Agents can connect and disconnect from,
  * one at a time.
  *
  * Operations are treated as asynchronous. The request methods have no
  * return values; instead, the side effects that happen because of requests
  * will be sent as events to an EventStream, which can be accessed through
  * the `subscribe` method. Getting the current state of the Trace returns
  * its value in a Future.
  */
trait TracingTarget {
	/** An identifier used to distinguish this TracingTarget from others. */
	def id: TraceId

	def subscribeToStateChanges(sub: EventStream[TracingTargetState] => Unit)(implicit exc: ExecutionContext): Future[Unit]
	def requestNewTraceConnection()(implicit exc: ExecutionContext): Future[Unit]
	def requestTraceEnd()(implicit exc: ExecutionContext): Future[Unit]
	def getState: Future[TracingTargetState]

	def traceData: TraceData
	def transientData: TransientTraceData

	def setDeletePending()(implicit exc: ExecutionContext): Future[Unit]
	def cancelPendingDeletion()(implicit exc: ExecutionContext): Future[Unit]
	def finalizeDeletion()(implicit exc: ExecutionContext): Future[Unit]

	def notifyFinishedLoading()(implicit exc: ExecutionContext): Future[Unit]
}

object AkkaTracingTarget {

	private sealed trait TargetRequest

	// ----
	// Messages handled internally by the StateMachine actor
	// ----
	private case object FinishedLoading extends TargetRequest
	private case object RequestTraceConnect extends TargetRequest
	private case object RequestTraceEnd extends TargetRequest
	private case class Subscribe(f: EventStream[TracingTargetState] => Unit) extends TargetRequest
	private case object RequestState extends TargetRequest

	private case class TraceConnected(trace: Trace)
	private case class TraceEnded(reason: TraceEndReason)

	private case object SetDeletePending extends TargetRequest
	private case object CancelPendingDeletion extends TargetRequest
	private case object FinalizeDeletion extends TargetRequest

	private case object Ack
	private case class AntiAck(msg: Option[String] = None)
	private def AntiAck(msg: String): AntiAck = AntiAck(Some(msg))

	private class TracingTargetImpl(val id: TraceId, actor: ActorRef, val traceData: TraceData, val transientData: TransientTraceData) extends TracingTarget with AskSupport {
		def subscribeToStateChanges(sub: EventStream[TracingTargetState] => Unit)(implicit exc: ExecutionContext) = getAckFuture(Subscribe(sub))
		def requestNewTraceConnection()(implicit exc: ExecutionContext) = getAckFuture(RequestTraceConnect)
		def requestTraceEnd()(implicit exc: ExecutionContext) = getAckFuture(RequestTraceEnd)

		def getState = {
			implicit val timeout = new Timeout(5.seconds)
			val reply = actor ? RequestState
			reply.mapTo[TracingTargetState]
		}

		def setDeletePending()(implicit exc: ExecutionContext): Future[Unit] = {
			val timeout = new Timeout(30.seconds)
			val replyFuture = actor.ask(SetDeletePending)(timeout)

			// the actor will reply with either CancelPendingDeletion or FinalizeDeletion,
			// the same as whichever message was sent while it was in the DeletePending state.
			replyFuture flatMap {
				case CancelPendingDeletion =>
					Future.failed { new Exception("Deletion was canceled") }
				case FinalizeDeletion =>
					Future successful ()
				case _ =>
					Future.failed { new Exception("Unexpected reply from TracingTarget actor") }
			}
		}

		def cancelPendingDeletion()(implicit exc: ExecutionContext) = getAckFuture(CancelPendingDeletion)
		def finalizeDeletion()(implicit exc: ExecutionContext) = getAckFuture(FinalizeDeletion)
		def notifyFinishedLoading()(implicit exc: ExecutionContext) = getAckFuture(FinishedLoading)

		/** Create a message 'ask' future that expects an `Ack` message in return.
		  * If an AntiAck is sent with a message, the future fails with an exception
		  * containing that message. If any other message is sent, the future fails
		  * with no exception message.
		  */
		private def getAckFuture(msg: Any)(implicit exc: ExecutionContext) = {
			implicit val timeout = new Timeout(5.seconds)
			val future = actor ? msg
			future flatMap {
				case Ack => Future successful ()
				case AntiAck(Some(msg)) => Future.failed { new IllegalStateException(msg) }
				case _ => Future.failed { new IllegalStateException }
			}
		}
	}

	def apply(actorSystem: ActorSystem, traceId: TraceId, traceData: TraceData, transientTraceData: TransientTraceData, jspMapper: Option[JspMapper]): TracingTarget = {
		val props = Props { new AkkaTracingTarget(traceId, traceData, transientTraceData, jspMapper) }
		val actorRef = actorSystem.actorOf(props)
		new TracingTargetImpl(traceId, actorRef, traceData, transientTraceData)
	}

}

/** An Akka Actor that can be in one of 7 states while managing a Trace.
  *
  * - **Loading** - The server is still analyzing the uploaded file. The trace
  * is not ready for any real interaction.
  * - **Idle** - The trace is inactive. May transition to *Connecting* or *DeletePending*
  * depending on user interaction.
  * - **Connecting** - Waiting for a Tracer Agent to connect. Transitions to *Tracing*
  * once the connection is established.
  * - **Tracing** - A Tracer Agent is connected and running. May transition to *Ending*
  * with user input, or directly back to *Idle* if the agent ends the trace on its own.
  * - **Ending** - Waiting for the trace to finish after a stop command. Transitions back
  * to *Idle* once the trace has disconnected.
  * - **DeletePending** - A user has attempted to delete this trace. During this
  * time window, the deletion may be canceled (transition to *Idle*) or finalized
  * (transition to *Deleted*).
  * - **Deleted** - The pending deletion was finalized. This actor will soon
  * be terminated and its associated data deleted.
  */
class AkkaTracingTarget(traceId: TraceId, traceData: TraceData, transientTraceData: TransientTraceData, jspMapper: Option[JspMapper]) extends Actor {

	import AkkaTracingTarget._

	private var trace: Option[Trace] = None
	private val stateChanges = new EventSource[TracingTargetState]

	// import an ExecutionContext for running Futures
	import context.dispatcher

	def receive = StateLoading.receive

	class State(val s: TracingTargetState, val receive: Receive)

	/*
	 * Wrap any Receiver state so that the actor will accept
	 * Subscribe messages, allowing external things to be notified
	 * when an `Update` message is sent. Also react to a RequestState
	 * message by replying with the state `s`.
	 */
	private def State(s: TracingTargetState, body: Receive): State = {
		new State(s, body orElse {

			// set up the subscription, then reply with Ack
			case sub: Subscribe =>
				sub.f(stateChanges)
				sender ! Ack

			// reply to the sender with the current state
			case RequestState =>
				sender ! s

			// SetDeletePending is only accepted by the Idle state. Since
			// the implementation expects a reply, we reply immediately
			// with an error
			case SetDeletePending =>
				sender ! AntiAck("Cannot delete a Trace if it is not idle")

			// Any other "Request" that goes unhandled by the `body` is an error
			case msg: TargetRequest =>
				sender ! AntiAck(s"Unexpected $msg while in state $s")
		})
	}

	private def changeState(newState: State) = {
		context.become(newState.receive)
		println(s"$traceId target state changing to ${newState.s}")
		stateChanges fire newState.s
	}

	val StateLoading = State(TracingTargetState.Loading, {
		case FinishedLoading =>
			changeState(StateIdle)
			sender ! Ack
	})

	/** No activity currently going on.
	  * The state may be advanced by sending a `TraceRequested` message,
	  * which will cause the state to transition to the "Connecting" state.
	  */
	val StateIdle: State = State(TracingTargetState.Idle, {
		case RequestTraceConnect => onTraceRequested()

		case SetDeletePending => changeState { StateDeletePending(sender) }
	})

	/** Deletion has been finalized, and the actor will soon be terminated (at this
	  * point a PoisonPill message should be in the mailbox).
	  */
	val StateDeleted = State(TracingTargetState.Deleted, PartialFunction.empty)

	/** A deletion has been requested by the `requester`. It can either be canceled
	  * or finalized; in either case, the requester will be notified with the same message.
	  * If the deletion is canceled, the state goes back to Idle. If it is finalized,
	  * the state changes to Deleted and the actor will be terminated via PoisonPill.
	  */
	def StateDeletePending(requester: ActorRef) = State(TracingTargetState.DeletePending, {
		case CancelPendingDeletion =>
			requester ! CancelPendingDeletion
			changeState(StateIdle)
			sender ! Ack

		case FinalizeDeletion =>
			requester ! FinalizeDeletion
			changeState(StateDeleted)
			self ! PoisonPill
			sender ! Ack
	})

	/** Currently waiting for a new Tracer Agent to connect.
	  * The state will be advanced to "Tracing"
	  */
	val StateConnecting = State(TracingTargetState.Connecting, {
		case TraceConnected(t) => onTraceConnected(t)
	})

	/** A trace is currently connected and running.
	  * It may stop on its own, which would cause a TraceCompleted message.
	  * Or the user might request the trace to end, which would cause a
	  * TraceEndRequested message.
	  */
	val StateTracing = State(TracingTargetState.Running, {
		case RequestTraceEnd => onTraceEndRequested()
		case TraceEnded(reason) => onTraceCompleted(reason)
	})

	/** The user requested the trace to end. Once it does,
	  * the state will change back to Idle.
	  */
	val StateEnding = State(TracingTargetState.Ending, {
		case TraceEnded(reason) => onTraceCompleted(reason)
	})

	// ----
	// Implementation of the State changes below here
	// ----

	private def onTraceRequested(): Unit = {
		println("New Trace Requested")

		// ask for a new trace via the TraceServer
		val traceFuture = TraceServer.awaitNewTrace(traceData, jspMapper)

		// when a new trace comes in, send a Msg to update the state
		for (trace <- traceFuture) self ! TraceConnected(trace)

		// change the state to "Connecting"
		changeState(StateConnecting)
	}

	private def onTraceConnected(t: Trace): Unit = {
		println("New Trace Connected")

		// send a Msg to update the state when `t` completes
		for (reason <- t.completion) self ! TraceEnded(reason)

		// set up data management for the trace
		val dataManager = new StreamingTraceDataManager(traceData, transientTraceData, jspMapper)

		// remember this trace
		trace = Some(t)

		// start the trace
		t.start(dataManager)

		// change the state to "Tracing"
		changeState(StateTracing)
	}

	private def onTraceEndRequested(): Unit = {
		println("End Trace Requested")

		// tell the current trace to stop.
		// This will trigger the trace.completion future, which will change the state
		for (t <- trace) t.stop()

		// change the state for "Ending"
		changeState(StateEnding)
	}

	private def onTraceCompleted(reason: TraceEndReason): Unit = {
		println("Trace Finished")

		// clear the trace
		trace = None

		// change the state back to "Idle"
		changeState(StateIdle)
	}

}

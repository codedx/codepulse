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

import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import com.secdec.bytefrog.hq.trace.Trace
import com.secdec.bytefrog.hq.trace.TraceEndReason
import com.secdec.codepulse.data.jsp.JspMapper
import com.secdec.codepulse.data.model.ProjectData
import com.secdec.codepulse.data.model.ProjectId
import akka.actor._
import akka.pattern.AskSupport
import akka.util.Timeout
import reactive.EventSource
import reactive.EventStream
import com.secdec.bytefrog.hq.config.AgentConfiguration

sealed abstract class TracingTargetState(val name: String)
object TracingTargetState {
	case object Loading extends TracingTargetState("loading")
	case object LoadingFailed extends TracingTargetState("loading-failed")
	case object Idle extends TracingTargetState("idle")
	//	case object Connecting extends TracingTargetState("connecting")
	case object Running extends TracingTargetState("running")
	case object Ending extends TracingTargetState("ending")
	case object DeletePending extends TracingTargetState("delete-pending")
	case object Deleted extends TracingTargetState("deleted")
}

/** A token that represents the ability to finalize a pending deletion.
  * Calling `setDeletePending` will return an instance of DeletionKey,
  * and the call to `finalizeDeletion` needs to pass in the same key. If
  * `cancelPendingDeletion` is called, any existing DeletionKeys should
  * be invalidated.
  */
case class DeletionKey(val id: Int)

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
	def id: ProjectId

	def subscribeToStateChanges(sub: EventStream[TracingTargetState] => Unit)(implicit exc: ExecutionContext): Future[Unit]
	def requestTraceEnd()(implicit exc: ExecutionContext): Future[Unit]
	def getState: Future[TracingTargetState]

	def projectData: ProjectData
	def transientData: TransientTraceData

	private val deletionKeyGen = Iterator from 0 map { id => new DeletionKey(id) }
	protected def newDeletionKey = deletionKeyGen.next

	def connectTrace(trace: Trace)(implicit exc: ExecutionContext): Future[Unit]

	def setDeletePending()(implicit exc: ExecutionContext): (DeletionKey, Future[Unit])
	def cancelPendingDeletion()(implicit exc: ExecutionContext): Future[Unit]
	def finalizeDeletion(key: DeletionKey)(implicit exc: ExecutionContext): Future[Unit]

	def notifyLoadingFinished()(implicit exc: ExecutionContext): Future[Unit]
	def notifyLoadingFailed()(implicit exc: ExecutionContext): Future[Unit]

	/** Get the state of this trace. Since the operation is normally asynchronous,
	  * this method will block for up to 1 second to wait for the result of the
	  * future. If the state is returned normally, this method returns a `Some`
	  * containing the state. If the future times out or finished with an error,
	  * this method returns `None`.
	  */
	def getStateSync: Option[TracingTargetState] = {
		val atMost = 1.second
		val stateFuture = getState
		try {
			Await.ready(stateFuture, atMost)
		} catch {
			case e: TimeoutException => // don't care
		}
		stateFuture.value flatMap {
			case Success(state) => Some(state)
			case Failure(_) => None
		}
	}
}

object AkkaTracingTarget {

	private sealed trait TargetRequest

	// ----
	// Messages handled internally by the StateMachine actor
	// ----
	private case object LoadingFinished extends TargetRequest
	private case object LoadingFailed extends TargetRequest
	private case object RequestTraceEnd extends TargetRequest
	private case class Subscribe(f: EventStream[TracingTargetState] => Unit) extends TargetRequest
	private case object RequestState extends TargetRequest

	private case class TraceConnected(trace: Trace) extends TargetRequest
	private case class TraceEnded(reason: TraceEndReason)

	/* SetDeletePending and FinalizeDeletion use an `inc` id, e.g.
	 * `FinalizeDeletion(2)` cannot affect the trace when it is in
	 * the state for `SetDeletePending(3)`.
	 */
	private case class SetDeletePending(key: DeletionKey) extends TargetRequest
	private case class FinalizeDeletion(key: DeletionKey) extends TargetRequest
	private case object CancelPendingDeletion extends TargetRequest

	private case object Ack
	private case class AntiAck(msg: Option[String] = None)
	private def AntiAck(msg: String): AntiAck = AntiAck(Some(msg))

	private class TracingTargetImpl(val id: ProjectId, actor: ActorRef, val projectData: ProjectData, val transientData: TransientTraceData) extends TracingTarget with AskSupport {
		def subscribeToStateChanges(sub: EventStream[TracingTargetState] => Unit)(implicit exc: ExecutionContext) = getAckFuture(Subscribe(sub))
		def requestTraceEnd()(implicit exc: ExecutionContext) = getAckFuture(RequestTraceEnd)

		def getState = {
			implicit val timeout = new Timeout(5.seconds)
			val reply = actor ? RequestState
			reply.mapTo[TracingTargetState]
		}

		def setDeletePending()(implicit exc: ExecutionContext): (DeletionKey, Future[Unit]) = {
			val timeout = new Timeout(30.seconds)

			val deletionKey = newDeletionKey
			val replyFuture = actor.ask(SetDeletePending(deletionKey))(timeout)

			// the actor will reply with either CancelPendingDeletion or FinalizeDeletion,
			// the same as whichever message was sent while it was in the DeletePending state.
			val deletionFuture = replyFuture flatMap {
				case CancelPendingDeletion =>
					Future.failed { new Exception("Deletion was canceled") }
				case FinalizeDeletion =>
					Future successful ()
				case _ =>
					Future.failed { new Exception("Unexpected reply from TracingTarget actor") }
			}

			deletionKey -> deletionFuture
		}

		def connectTrace(trace: Trace)(implicit exc: ExecutionContext) = getAckFuture(TraceConnected(trace))

		def cancelPendingDeletion()(implicit exc: ExecutionContext) = getAckFuture(CancelPendingDeletion)
		def finalizeDeletion(key: DeletionKey)(implicit exc: ExecutionContext) = getAckFuture(FinalizeDeletion(key))
		def notifyLoadingFinished()(implicit exc: ExecutionContext) = getAckFuture(LoadingFinished)
		def notifyLoadingFailed()(implicit exc: ExecutionContext) = getAckFuture(LoadingFailed)

		/** Create a message 'ask' future that expects an `Ack` message in return.
		  * If an AntiAck is sent with a message, the future fails with an exception
		  * containing that message. If any other message is sent, the future fails
		  * with no exception message.
		  */
		private def getAckFuture(msg: TargetRequest)(implicit exc: ExecutionContext) = {
			implicit val timeout = new Timeout(5.seconds)
			val future = actor ? msg
			future flatMap {
				case Ack => Future successful ()
				case AntiAck(Some(msg)) => Future.failed { new IllegalStateException(msg) }
				case _ => Future.failed { new IllegalStateException }
			}
		}
	}

	def apply(actorSystem: ActorSystem, projectId: ProjectId, projectData: ProjectData, transientTraceData: TransientTraceData, jspMapper: Option[JspMapper]): TracingTarget = {
		val props = Props { new AkkaTracingTarget(projectId, projectData, transientTraceData, jspMapper) }
		val actorRef = actorSystem.actorOf(props)
		new TracingTargetImpl(projectId, actorRef, projectData, transientTraceData)
	}

}

/** An Akka Actor that can be in one of 7 states while managing a Trace.
  *
  * - **Loading** - The server is still analyzing the uploaded file. The trace
  * is not ready for any real interaction.
  * - **LoadingFailed** - Terminal state, where the Loading phase failed and whatever
  * data was generated should soon be deleted.
  * - **Idle** - The trace is inactive. May transition to *Tracing* or *DeletePending*
  * depending on user interaction.
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
class AkkaTracingTarget(projectId: ProjectId, projectData: ProjectData, transientTraceData: TransientTraceData, jspMapper: Option[JspMapper]) extends Actor {

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
		println(s"$projectId target state changing to ${newState.s}")
		stateChanges fire newState.s
	}

	val StateLoading = State(TracingTargetState.Loading, {
		case LoadingFinished =>
			changeState(StateIdle)
			sender ! Ack

		case LoadingFailed =>
			changeState(StateLoadingFailed)
			self ! PoisonPill
			sender ! Ack
	})

	val StateLoadingFailed = State(TracingTargetState.LoadingFailed, PartialFunction.empty)

	/** No activity currently going on.
	  * The state may be advanced by sending a `TraceRequested` message,
	  * which will cause the state to transition to the "Connecting" state.
	  */
	val StateIdle: State = State(TracingTargetState.Idle, {
		//		case RequestTraceConnect => onTraceRequested()
		case TraceConnected(t) =>
			onTraceConnected(t)
			sender ! Ack

		case SetDeletePending(key) => changeState { StateDeletePending(sender, key) }
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
	def StateDeletePending(requester: ActorRef, key: DeletionKey) = State(TracingTargetState.DeletePending, {
		case CancelPendingDeletion =>
			requester ! CancelPendingDeletion
			changeState(StateIdle)
			sender ! Ack

		case FinalizeDeletion(`key`) =>
			println(s"Finalizing deletion using $key")
			requester ! FinalizeDeletion
			changeState(StateDeleted)
			self ! PoisonPill
			sender ! Ack
		case FinalizeDeletion(invalidKey) =>
			sender ! AntiAck(s"Won't finalize deletion due to invalid key: $invalidKey")

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

	private def onTraceConnected(t: Trace): Unit = {
		println("New Trace Connected")

		// send a Msg to update the state when `t` completes
		for (reason <- t.completion) self ! TraceEnded(reason)

		// reconfigure the trace's settings so that it instruments the packages/jsps from this project
		val traceSettings = TraceSettingsCreator.generateTraceSettings(projectData, jspMapper)
		t.reconfigureAgent(traceSettings, AgentConfiguration())

		// set up data management for the trace
		val dataManager = new StreamingTraceDataManager(projectData, transientTraceData, jspMapper)

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

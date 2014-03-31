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

import scala.concurrent.Future
import com.secdec.bytefrog.hq.trace.Trace
import com.secdec.bytefrog.hq.trace.TraceEndReason
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.AskSupport
import akka.util.Timeout
import reactive.EventSource
import reactive.EventStream
import com.secdec.codepulse.data.trace.TraceId
import com.secdec.codepulse.data.trace.TraceData

sealed trait TracingTargetEvent
object TracingTargetEvent {
	case object Connecting extends TracingTargetEvent
	case object Connected extends TracingTargetEvent
	case class Started(traceId: TraceId) extends TracingTargetEvent
	case class Finished(reason: TraceEndReason) extends TracingTargetEvent
}

sealed trait TracingTargetState
object TracingTargetState {
	case object Idle extends TracingTargetState
	case object Connecting extends TracingTargetState
	case object Running extends TracingTargetState
	case object Ending extends TracingTargetState
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

	def subscribe(sub: EventStream[TracingTargetEvent] => Unit): Unit
	def requestNewTraceConnection(): Unit
	def requestTraceEnd(): Unit
	def getState: Future[TracingTargetState]

	def traceData: TraceData
	def transientData: TransientTraceData
}

object AkkaTracingTarget {

	// ----
	// Messages handled internally by the StateMachine actor
	// ----

	private case object RequestTraceConnect
	private case object RequestTraceEnd
	private case class Subscribe(f: EventStream[TracingTargetEvent] => Unit)
	private case object RequestState

	private case class TraceConnected(trace: Trace)
	private case class TraceEnded(reason: TraceEndReason)

	// implicit Timeout used for the Akka ask (?) method in getState
	private implicit val timeout = new Timeout(5000)

	private class TracingTargetImpl(val id: TraceId, actor: ActorRef, val traceData: TraceData, val transientData: TransientTraceData) extends TracingTarget with AskSupport {
		def subscribe(sub: EventStream[TracingTargetEvent] => Unit) = actor ! Subscribe(sub)
		def requestNewTraceConnection() = actor ! RequestTraceConnect
		def requestTraceEnd() = actor ! RequestTraceEnd
		def getState = {
			val reply = actor ? RequestState
			reply.mapTo[TracingTargetState]
		}
	}

	def apply(actorSystem: ActorSystem, traceId: TraceId, traceData: TraceData, transientTraceData: TransientTraceData): TracingTarget = {
		val props = Props(classOf[AkkaTracingTarget], traceId, traceData, transientTraceData)
		val actorRef = actorSystem.actorOf(props)
		new TracingTargetImpl(traceId, actorRef, traceData, transientTraceData)
	}

}

/** An Akka Actor that can be in one of 4 states while managing a Trace.
  *
  * Idle - nothing is going on
  * Connecting - waiting for a Tracer Agent to connect
  * Tracing - running a connected trace
  * Ending - waiting for the trace to finish after a stop command
  *
  * As the trace progresses and as user input is made, the state
  * will transition in the following ways:
  * {{{
  * Idle -> Connecting -> Tracing -> Ending -> Idle
  * _                             \____>_____/
  * }}}
  *
  * Note that in the tracing state, depending on the manner of the trace
  * finishing, it may or may not transition through the Ending state before
  * returning to Idle.
  */
class AkkaTracingTarget(traceId: TraceId, traceData: TraceData, transientTraceData: TransientTraceData) extends Actor {

	import AkkaTracingTarget._

	private var trace: Option[Trace] = None
	private val events = new EventSource[TracingTargetEvent]

	// import an ExecutionContext for running Futures
	import context.dispatcher

	/* Traces for this analysis run will use a precalculated method correlators to associate
	 * binary method signatures to source node ids that are stored in Code Pulse's database.
	 */
	//	private val methodCorrelator = new PrecalculatedMethodCorrelator(analysis)

	def receive = StateIdle

	/*
	 * Wrap any Receiver state so that the actor will accept
	 * Subscribe messages, allowing external things to be notified
	 * when an `Update` message is sent. Also react to a RequestState
	 * message by replying with the state `s`.
	 */
	private def State(s: TracingTargetState)(body: Receive): Receive = {
		body orElse {
			case sub: Subscribe => sub.f(events)
			case RequestState => sender ! s
		}
	}

	/** No activity currently going on.
	  * The state may be advanced by sending a `TraceRequested` message,
	  * which will cause the state to transition to the "Connecting" state.
	  */
	val StateIdle = State(TracingTargetState.Idle) {
		case RequestTraceConnect => onTraceRequested()
	}

	/** Currently waiting for a new Tracer Agent to connect.
	  * The state will be advanced to "Tracing"
	  */
	val StateConnecting = State(TracingTargetState.Connecting) {
		case TraceConnected(t) => onTraceConnected(t)
	}

	/** A trace is currently connected and running.
	  * It may stop on its own, which would cause a TraceCompleted message.
	  * Or the user might request the trace to end, which would cause a
	  * TraceEndRequested message.
	  */
	val StateTracing = State(TracingTargetState.Running) {
		case RequestTraceEnd => onTraceEndRequested()
		case TraceEnded(reason) => onTraceCompleted(reason)
	}

	/** The user requested the trace to end. Once it does,
	  * the state will change back to Idle.
	  */
	val StateEnding = State(TracingTargetState.Ending) {
		case TraceEnded(reason) => onTraceCompleted(reason)
	}

	// ----
	// Implementation of the State changes below here
	// ----

	private def onTraceRequested(): Unit = {
		println("New Trace Requested")

		// ask for a new trace via the TraceServer
		val traceFuture = TraceServer awaitNewTrace traceData

		// when a new trace comes in, send a Msg to update the state
		for (trace <- traceFuture) self ! TraceConnected(trace)

		events fire TracingTargetEvent.Connecting

		// change the state to "Connecting"
		context.become(StateConnecting)
	}

	private def onTraceConnected(t: Trace): Unit = {
		println("New Trace Connected")

		events fire TracingTargetEvent.Connected

		// send a Msg to update the state when `t` completes
		for (reason <- t.completion) self ! TraceEnded(reason)

		// set up data management for the trace
		val dataManager = new StreamingTraceDataManager(traceData, transientTraceData)

		// remember this trace
		trace = Some(t)

		// start the trace
		t.start(dataManager)

		// send a TraceStarted update event
		events fire TracingTargetEvent.Started(traceId)

		// change the state to "Running"
		context.become(StateTracing)
	}

	private def onTraceEndRequested(): Unit = {
		println("End Trace Requested")

		// tell the current trace to stop.
		// This will trigger the trace.completion future, which will change the state
		for (t <- trace) t.stop()

		// change the state for "Ending"
		context.become(StateEnding)
	}

	private def onTraceCompleted(reason: TraceEndReason): Unit = {
		println("Trace Finished")

		// clear the trace
		trace = None

		// fire an event that says that/why the trace finished
		events fire TracingTargetEvent.Finished(reason)

		// change the state back to "Idle"
		context.become(StateIdle)
	}

}

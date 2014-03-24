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

package com.secdec.bytefrog.hq.agent

import com.secdec.bytefrog.common.message.AgentOperationMode
import com.secdec.bytefrog.hq.connect.ControlConnection
import com.secdec.bytefrog.hq.errors._
import com.secdec.bytefrog.hq.protocol.ControlMessage._
import com.secdec.bytefrog.hq.trace.StopMethod
import com.secdec.bytefrog.hq.trace.players.LoopPlayer
import com.secdec.bytefrog.hq.util.CompletionHooks

import reactive.EventSource
import reactive.EventStream

/** A [[LoopingThread]] that manages a [[ControlConnection]] to a running Agent, sending and
  * receiving commands, and managing state (via an [[AgentStateManager]]).
  */
class AgentController(protected val traceErrorController: TraceErrorController, agentControlConnection: ControlConnection)
	extends LoopPlayer with CompletionHooks {

	private var lastStateEntered = System.currentTimeMillis
	private val stateManager = new AgentStateManager(agentControlConnection, traceErrorController)

	private var _lastHeartbeatTime: Long = _
	private var _lastHeartbeat: Heartbeat = null

	/** Refers to the time (millis since epoch) of the last heartbeat received from the Agent */
	def lastHeartbeatTime = _lastHeartbeatTime

	/** Refers to the last heartbeat received from the Agent */
	def lastHeartbeat = _lastHeartbeat

	private val classTransformEventSource = new EventSource[String]
	private val classIgnoreEventSource = new EventSource[String]
	private val classTransformFailEventSource = new EventSource[String]

	/** An observable stream of names of classes that get transformed by the Agent */
	def classTransformEvents: EventStream[String] = classTransformEventSource

	/** An observable stream of names of classes that the Agent fails to transform */
	def classTransformFailEvents: EventStream[String] = classTransformFailEventSource

	/** An observable stream of names of classes that get ignored by the Agent */
	def classIgnoreEvents: EventStream[String] = classIgnoreEventSource

	/** An observable stream of data breaks reported by Agent */
	def dataBreaks: EventStream[Int] = dataBreaksSource
	private val dataBreaksSource = new EventSource[Int]

	/** Observable stream of new agent states */
	def agentStateChange = stateManager.agentStateChange

	/** Checks if the latest heartbeat was in an expected mode. Generally if this method
	  * returns `false`, that's an error.
	  */
	def wasLastHeartbeatModeExpected = lastHeartbeat != null && stateManager.isHeartbeatModeExpected(lastHeartbeat.operationMode)

	/** @return the time of the last state change reported by the underlying state manager
	  */
	def lastStateChange = stateManager.lastStateChange

	override protected def preLoop() = _lastHeartbeatTime = System.currentTimeMillis

	override protected def doLoop() = agentControlConnection.recieve() match {
		// received a heartbeat
		case hb @ Heartbeat(mode, bufferSize) =>
			if (mode == AgentOperationMode.Shutdown &&
				_lastHeartbeat != null && _lastHeartbeat.operationMode != mode) {
				stateManager.handleCommand(AgentStateCommand.ReceivedShutdown)
			}

			_lastHeartbeatTime = System.currentTimeMillis
			_lastHeartbeat = hb

		// received an error
		case Error(error) => traceErrorController.reportTraceError(UnexpectedError(s"Error received from Agent: $error"))

		// EOF means the Agent closed the connection, so we're done
		case EOF =>
			shutdown
			traceErrorController.reportTraceError(ConditionalError("Control connection closed"))

		case ClassTransformed(className) => classTransformEventSource fire className
		case ClassIgnored(className) => classIgnoreEventSource fire className
		case ClassTransformFailed(className) => classTransformFailEventSource fire className

		case DataBreak(seq) => dataBreaksSource fire seq

		// Any other message is unexpected
		case _ =>
			shutdown
			traceErrorController.reportTraceError(UnexpectedError("Bad control message"))
	}

	override protected def postLoop = {
		complete
	}

	override def stop(how: StopMethod) {
		super.stop(how)
		how match {
			case StopMethod.GracefulStop => stopTracing
			case _ =>
		}
	}

	def currentState: AgentState = stateManager.current

	def startTracing() = stateManager.handleCommand(AgentStateCommand.Start)
	def stopTracing() = stateManager.handleCommand(AgentStateCommand.Stop)

	/** Resume tracing (i.e., unpause and unsuspend) */
	def resumeTracing() = stateManager.handleCommand(AgentStateCommand.Resume)

	/** Pause tracing (i.e., pause execution of program) */
	def pauseTracing() = stateManager.handleCommand(AgentStateCommand.Pause)

	/** Suspend tracing (i.e., keep running, but stop collecting data) */
	def suspendTracing() = stateManager.handleCommand(AgentStateCommand.Suspend)
}
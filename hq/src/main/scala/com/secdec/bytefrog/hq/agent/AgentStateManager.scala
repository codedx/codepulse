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

package com.codedx.codepulse.hq.agent

import com.codedx.codepulse.agent.common.message.AgentOperationMode
import com.codedx.codepulse.hq.connect.ControlConnection
import com.codedx.codepulse.hq.errors.TraceErrorController
import com.codedx.codepulse.hq.protocol.ControlMessage

import reactive.EventSource
import reactive.EventStream

sealed trait AgentStateCommand
object AgentStateCommand {
	case object Start extends AgentStateCommand
	case object Stop extends AgentStateCommand
	case object ReceivedShutdown extends AgentStateCommand
	case object Pause extends AgentStateCommand
	case object Suspend extends AgentStateCommand
	case object Resume extends AgentStateCommand
}

sealed trait AgentState
object AgentState {
	case object Initializing extends AgentState
	case object Tracing extends AgentState
	case object Paused extends AgentState
	case object Suspended extends AgentState
	case object ShuttingDown extends AgentState
	case object Unknown extends AgentState
}

class AgentStateManager(agentControlConnection: ControlConnection, traceErrorController: TraceErrorController) {
	private var currentState: InternalState = Initializing
	private var _lastStateChange: Long = System.currentTimeMillis

	/** An event fired when the agent state changes */
	def agentStateChange: EventStream[AgentState] = agentStateChangeEvent
	private val agentStateChangeEvent = new EventSource[AgentState]

	def transitionTo(newState: InternalState) {
		synchronized {
			if (!isNewStateValid(newState))
				throw new IllegalStateException("invalid state transition")

			currentState.exit(newState)
			newState.enter(currentState)
			currentState = newState
			_lastStateChange = System.currentTimeMillis
			agentStateChangeEvent fire currentState.state
		}
	}

	private def isNewStateValid(newState: InternalState): Boolean = currentState.canExit(newState) && newState.canEnter(currentState)

	def isHeartbeatModeExpected(mode: AgentOperationMode) = currentState.isHeartbeatModeExpected(mode)

	def current = currentState.state
	def lastStateChange = _lastStateChange

	def handleCommand(command: AgentStateCommand) = (currentState.handleCommand orElse baseCommandHandler)(command) match {
		case Some(newState) if (newState != currentState) => transitionTo(newState)
		case _ =>
	}

	private val baseCommandHandler: PartialFunction[AgentStateCommand, Option[InternalState]] = {
		case AgentStateCommand.Stop => Some(ShuttingDown)
		case AgentStateCommand.ReceivedShutdown => Some(ShuttingDown)
		case AgentStateCommand.Pause => Some(Paused)
		case AgentStateCommand.Suspend => Some(Suspended)
		case AgentStateCommand.Resume => Some(Tracing)
		case _ => throw new IllegalStateException("unexpected command")
	}

	sealed private trait InternalState {
		def state: AgentState
		def isHeartbeatModeExpected(mode: AgentOperationMode): Boolean

		def canEnter(oldState: InternalState): Boolean = true
		def canExit(newState: InternalState): Boolean = true

		def handleCommand = PartialFunction.empty[AgentStateCommand, Option[InternalState]]

		def enter(oldState: InternalState) {}
		def exit(newState: InternalState) {}
	}

	private case object Initializing extends InternalState {
		private var suspended = false

		def state = AgentState.Initializing
		def isHeartbeatModeExpected(mode: AgentOperationMode) = mode == AgentOperationMode.Initializing

		override def canEnter(oldState: InternalState) = false

		override def handleCommand = {
			case AgentStateCommand.Suspend =>
				suspended = true
				None
			case AgentStateCommand.Resume =>
				suspended = false
				None
			case AgentStateCommand.Start => suspended match {
				case true => Some(Suspended)
				case false => Some(Tracing)
			}
		}

		override def exit(newState: InternalState) = {
			// special handling, we need to write suspend before start
			val msg1 = if (newState == Suspended) List(ControlMessage.Suspend) else Nil

			val msgs = msg1 :+ ControlMessage.Start
			agentControlConnection.send(msgs: _*)
		}
	}

	private case object Tracing extends InternalState {
		def state = AgentState.Tracing
		def isHeartbeatModeExpected(mode: AgentOperationMode) = mode == AgentOperationMode.Tracing
	}

	private case object Paused extends InternalState {
		def state = AgentState.Paused
		def isHeartbeatModeExpected(mode: AgentOperationMode) = mode == AgentOperationMode.Paused

		override def enter(oldState: InternalState) = agentControlConnection.send(ControlMessage.Pause)

		override def exit(newState: InternalState) = agentControlConnection.send(ControlMessage.Unpause)
	}

	private case object Suspended extends InternalState {
		def state = AgentState.Suspended
		def isHeartbeatModeExpected(mode: AgentOperationMode) = mode == AgentOperationMode.Suspended

		override def enter(oldState: InternalState) = oldState match {
			case Initializing => // special handling, do nothing
			case _ => agentControlConnection.send(ControlMessage.Suspend)
		}

		override def exit(newState: InternalState) = agentControlConnection.send(ControlMessage.Unsuspend)
	}

	private case object ShuttingDown extends InternalState {
		def state = AgentState.ShuttingDown
		def isHeartbeatModeExpected(mode: AgentOperationMode) = mode == AgentOperationMode.Shutdown

		override def canExit(newState: InternalState) = false

		override def handleCommand = {
			case _ => None // ignore all commands now
		}

		override def enter(oldState: InternalState) = {
			agentControlConnection.send(ControlMessage.Stop)
			traceErrorController.setTraceShuttingDown
		}
	}
}
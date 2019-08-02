/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.codedx.codepulse.hq.trace

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.codedx.codepulse.hq.agent.AgentController
import com.codedx.codepulse.hq.config._
import com.codedx.codepulse.hq.connect._
import com.codedx.codepulse.hq.data.DataConnectionController
import com.codedx.codepulse.hq.data.collection.DataCollector
import com.codedx.codepulse.hq.data.processing.DataRouter
import com.codedx.codepulse.hq.errors.TraceErrorController
import com.codedx.codepulse.hq.monitor._
import com.codedx.codepulse.hq.protocol.ControlMessage
import com.codedx.codepulse.hq.util.Completable
import com.codedx.codepulse.hq.util.DoOnce
import com.codedx.codepulse.hq.util.Startable
import com.codedx.codepulse.utility.Loggable

import org.apache.commons.lang3.exception._

import reactive.EventStream
import reactive.Observing

/** Provides entry points for HQ to initiate a Trace.
  */
object Trace {

	/** Gets a Trace. This waits for an agent to connect, configures the agent, and then provides that agent
	  * connection to the new Trace object.
	  *
	  * @param traceConfigurationProvider A function that will be called to get the configuration when the agent connects.
	  * @param server The SocketServer to be used to accept Control and Data connections for the returned Trace
	  *
	  * @return A Future that represents the Trace that will eventually be created. Since the Trace cannot
	  * technically be created until a ControlConnection is received, and the ControlConnection cannot connect
	  * without receiving the configuration provided in this method, we use scala's Futures to represent the
	  * waiting process.
	  */
	def getTrace(traceConfigurationProvider: () => (TraceSettings, AgentConfiguration, HQConfiguration, MonitorConfiguration))(implicit server: SocketServer): Future[Trace] = {
		server.traceRegistry.freshRunId match {
			case None => Future.failed(new Exception("Can't assign a run id"))
			case Some(runId) => {
				lazy val (traceConfiguration, agentConfiguration, hqConfiguration, monitorConfiguration) = traceConfigurationProvider()

				lazy val config = Configuration(runId, traceConfiguration, agentConfiguration)
				lazy val configMsg = ControlMessage.Configuration(config)

				// request an incoming control connection
				val controlFuture = server.controlConnector.getControlConnection(() => configMsg)

				for {
					controlConnection <- controlFuture
				} yield {
					val trace = new Trace(runId, controlConnection, hqConfiguration, monitorConfiguration)
					server.traceRegistry registerTrace trace
					trace
				}
			}
		}
	}
}

sealed trait TraceEndReason
object TraceEndReason {
	/** The trace ended normally */
	case object Normal extends TraceEndReason

	/** The trace was halted, and any data deleted */
	case object Halted extends TraceEndReason
}

/** Encapsulates all of the moving parts of a Trace in progress. Agent controller; data controllers;
  * file dump; health monitors; and connections. A Trace exposes its own `completion`, which can have
  * handlers attached to it; a Trace is completed once the connected Agent's application finishes
  * sending events. A Trace can also be shut down, interrupting the entire process if it hasn't already
  * completed.
  *
  * @param runId A numeric identifier that should be able to distinguish this trace from all other
  * currently-running traces.
  * @param controlConnection The main connection to the attached Agent.
  * @param hqConfig The HQ configuration.
  * @param monitorConfig The HQ monitor configuration.
  */
class Trace(val runId: Byte, controlConnection: ControlConnection, hqConfig: HQConfiguration, monitorConfig: MonitorConfiguration)
	extends HasTraceSegmentBuilder with Observing with Startable[TraceDataManager] with Completable[TraceEndReason] with Loggable {

	// The trace output settings are provided upon trace startup
	private var dataManager: TraceDataManager = _

	// Keep track of internal trace players who need their lifetime managed
	private val players = new TracePlayerManager

	// the thing that everyone comes to with their problems
	val errorController = new TraceErrorController

	// reaction to errors
	for (error <- errorController.fatalErrors.takeWhile { _ => !completion.isCompleted }) {
		logger.error(s"Killing trace because of fatal error: ${error.errorMessage}")
		for (exception <- error.exception) {
			logger.error(ExceptionUtils.getStackTrace(exception))
			logger.error(ExceptionUtils.getRootCauseMessage(exception))
			logger.error(ExceptionUtils.getStackTrace(ExceptionUtils.getRootCause(exception)))
		}

		// we need to halt the trace on a fatal error
		kill
	}

	// a health monitor manager
	private val status = new TraceStatus

	// the thing that handles control messages between hq and the agent
	private val agentController = new AgentController(errorController, controlConnection)

	// handle data breaks
	for (break <- agentController.dataBreaks) {
		dataCollector reportDataBreak break
	}

	def classTransformEvents: EventStream[String] = agentController.classTransformEvents
	def classTransformFailEvents: EventStream[String] = agentController.classTransformFailEvents
	def classIgnoreEvents: EventStream[String] = agentController.classIgnoreEvents

	def agentStateChange = agentController.agentStateChange

	// initialize the trace
	initialize

	/** The version of the underlying messaging protocol used by the connections to the Agent */
	val protocolVersion = controlConnection.protocolVersion

	/** The ID of the project associated with the trace */
	val projectId = controlConnection.projectId

	private lazy val dataRouter = {
		val router = new DataRouter(errorController)

		// add health monitor for router
		status addHealthMonitor new DataRouterMonitor(router)

		// make sure the router gets cleaned up
		players += router

		router
	}

	private lazy val dataCollector = {
		val collector = new DataCollector(errorController, dataRouter, hqConfig.sortQueueInitialSize, hqConfig.dataQueueMaximumSize)
		players += collector
		collector
	}

	/** a bunch of threads that handle incoming data from the agent */
	private val dataControllers = ListBuffer[DataConnectionController]()

	/** Add a new connection that provides incoming data. The connection
	  * will be used to provide data from the connected Agent to the data
	  * dumping system.
	  * @param dc The DataConnection to add to this Trace.
	  */
	def addDataConnection(dc: DataConnection): Boolean = synchronized {
		val controller = new DataConnectionController(dc, dataCollector)
		controller.start
		dataControllers += controller
		players += controller
		true
	}

	/** An "Event" representing changes in the trace's health status */
	def healthChanges = status.healthChanges

	/** A map representing the current health of each of the trace's components */
	def currentHealth = status.currentHealth

	/** An event for monitor data updates */
	def monitorData = status.monitorData

	/** Looks up current monitor data for a given component */
	def currentMonitorData[T <: TraceComponentMonitorData](component: TraceComponent) = status.currentMonitorData[T](component)

	/** Does pre-start initialization to get agent controller up and running, and pre-existing
	  * shutdown-able things registered.
	  */
	private def initialize() {
		// When agentController decides it is done, stop the trace
		agentController onComplete {
			stop
		}

		// When data processing is done, we've successfully completed the trace
		dataRouter onComplete {
			finishTrace(TraceEndReason.Normal)
		}

		// set up health monitors
		status addHealthMonitor new AgentHealthMonitor(agentController,
			monitorConfig.heartbeatInterval,
			monitorConfig.maxMissedHeartbeats,
			monitorConfig.modeChangeDelay)

		// start up the agent controller
		agentController.start

		// status and agent controller are players in the trace
		players ++= Seq(status, agentController)
	}

	/** Manages end-of-life of trace and cleans up any players, then fires off the completion hook.
	  * May only be called once!
	  */
	val finishTrace = DoOnce { (reason: TraceEndReason) =>
		// clean up the trace first, because in the case of failure, we need to delete potentially open files
		players.cleanup
		if (isStarted) {
			// if tracing hadn't started yet, segmentManager and dataManager will be null
			segmentManager.complete(System.currentTimeMillis)
			dataManager.finish(reason, isStarted)
		}

		complete(reason)
	}

	val stopPlayers = DoOnce { (method: StopMethod) =>
		players.stop(method)
	}

	/** Reconfigure the agent
	  */
	def reconfigureAgent(traceSettings: TraceSettings, agentConfig: AgentConfiguration) {
		if (isStarted) throw new IllegalStateException("Cannot reconfigure agent after trace has started")

		val config = Configuration(runId, traceSettings, agentConfig)
		val configMsg = ControlMessage.Configuration(config)

		controlConnection.send(configMsg)
	}

	/** Stops the current trace by detaching the agent. Data processing is finished normally. */
	def stop() {
		if (isStarted) {
			stopPlayers(StopMethod.GracefulStop)
		}
	}

	/** Kills the current trace - immediately tears everything down and cleans up everything. */
	def kill() {
		if (isStarted) {
			stopPlayers(StopMethod.ImmediateHalt)
		}

		finishTrace(TraceEndReason.Halted)
	}

	/** Startup block. Finishes initialization as required and gets the trace actually running.
	  */
	protected def doStart(tdm: TraceDataManager) = {
		// setup the data processing
		dataManager = tdm
		dataManager.setupDataProcessors(dataRouter)
		segmentManager = dataManager.setupSegmentProcessing(startTime)

		// start the data collector
		dataCollector.start

		agentController.startTracing
	}

	/** The current agent state */
	def agentState = agentController.currentState

	/** Tell agent to pause application execution */
	def pauseApplication() = agentController.pauseTracing

	/** Tell agent to suspend tracing */
	def suspendTracing() = agentController.suspendTracing

	/** Tell agent to resume execution and tracing */
	def resume() = agentController.resumeTracing
}
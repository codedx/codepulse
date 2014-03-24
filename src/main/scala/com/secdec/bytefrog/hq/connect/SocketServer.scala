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

package com.secdec.bytefrog.hq.connect

import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import com.secdec.bytefrog.common.connect.SocketConnection
import com.secdec.bytefrog.hq.util.LoopingThread
import com.secdec.bytefrog.hq.util.DaemonThreadFactory

object SocketServer {
	/** Creates a SocketServer on the given `port` that uses the singleton trace connector and registry
	  * for the other constructor arguments.
	  */
	def default(port: Int) = new SocketServer(port, TraceControlConnector, TraceRegistry)

	def unconfigured = new SocketServer(0, TraceControlConnector, TraceRegistry)
}

/** A SocketServer is the point of contact for Agents to connect to HQ. It runs as a Thread,
  * accepting incoming Socket connections on the given `port`, and running a [[ClientGreeter]]
  * for each connection.
  *
  * @param port The port number to accept connections.
  * @param controlConnector A middleman that can be used to associate new ControlConnections
  * with agent configurations, which are used to complete the "Hello" handshake.
  * @param traceRegistry A middleman that can be used to locate existing Traces, so that new
  * DataConnections can be added to the appropriate Trace.
  */
class SocketServer(
	private var listenPort: Int,
	val controlConnector: TraceControlConnector,
	val traceRegistry: TraceRegistry) extends LoopingThread {

	//open the server socket
	private var serverSocket: Option[ServerSocket] = None
	val executor = Executors.newFixedThreadPool(2, DaemonThreadFactory)

	def port = listenPort

	def setPort(port: Int) {
		val oldSocket = serverSocket
		listenPort = port

		if (listenPort > 0) {
			val newSocket = new ServerSocket(listenPort)
			/* accepting connections should time out after 2 seconds
			 this way it won't block forever trying to connect when
			 it should be shutting down. */
			newSocket.setSoTimeout(2000)

			serverSocket = Some(newSocket)
		} else {
			serverSocket = None
		}

		oldSocket match {
			case Some(oldSocket) => oldSocket.close
			case _ =>
		}
	}

	setPort(listenPort)

	def doLoop = {

		try {
			serverSocket match {
				case Some(serverSocket) =>
					//get a new connection
					val clientSocket = serverSocket.accept

					//create a greeter to handle the new connection
					val client = new SocketConnection(clientSocket, true, false)
					val greeter = new ClientGreeter(client, controlConnector, traceRegistry)

					//submit the greeter to the executor
					executor.submit(greeter)

				case _ => Thread.sleep(2000)
			}
		} catch {
			case e: SocketTimeoutException => {
				//this should happen every 2 seconds, according to the serverSocket's timeout.
				//this is expected, since without a timeout, we can't cleanly shut down this thread
			}
			case e: SocketException => {
				// this will happen if the socket is closed while we are listening, which will happen
				// if the port is changed dynamically
			}
		}
	}

	override def postLoop = {
		serverSocket match {
			case Some(serverSocket) => serverSocket.close
			case _ =>
		}
	}

}
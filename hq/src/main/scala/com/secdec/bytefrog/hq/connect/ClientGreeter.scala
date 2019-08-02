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

package com.codedx.codepulse.hq.connect

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import com.codedx.codepulse.agent.common.connect.Connection
import com.codedx.codepulse.agent.common.message.{MessageConstantsV1, MessageConstantsV4}
import com.codedx.codepulse.hq.protocol.ControlMessage._
import com.codedx.codepulse.hq.protocol._

/** A Runnable that accepts a client connection and attempts to perform either the
  * "Control Handshake" or "Data Handshake". Depending on which handshake is performed,
  * the client connection will be passed off through either the `controlConnector` or
  * `traceRegistry`. If no handshake is performed (failure), the client connection will
  * be closed.
  *
  * @param client The connection to the client.
  * @param controlConnector The object used to trade a connection for an agent configuration
  * @param traceRegistry The object used to locate the appropriate trace, to attach new data connections.
  * @param protocolHelper A [[ProtocolHelper]] instance that gets used to determine the right
  * senders/receivers for connections based on their protocol version.
  */
class ClientGreeter(
	client: Connection,
	controlConnector: TraceControlConnector,
	traceRegistry: TraceRegistry,
	protocolHelper: ProtocolHelper = DefaultProtocolHelper) extends Runnable {

	import protocolHelper._

	def run = {
		//wrap the socket's IO with Data[Input|Output]Streams

		//wait for a "hello" message
		val firstByte = client.input.readByte
		val secondByte = client.input.readByte

		//the 2 bytes should either be [Hello, protocolVersion] or [DataHello, runId]
		(firstByte, secondByte) match {
			case (MessageConstantsV1.MsgHello, protocolVersion) => handleHello(protocolVersion, None)
			case (MessageConstantsV4.MsgProjectHello, protocolVersion) => {
				val projectId = client.input.readInt
				handleHello(protocolVersion, Some(projectId))
			}
			case (MessageConstantsV1.MsgDataHello, runId) => handleDataHello(runId)
			case _ =>
				latestProtocol.writeError(client.output, "Unexpected Input Format")
				client.close
		}
	}

	/** Handle the client connection as an incoming control connection. As long as the
	  * `senderOpt` is defined, the client will be added to the `controlConnector` in
	  * order to receive a Configuration message, which will be used to reply to the
	  * "hello". Otherwise, the client will be closed with an error.
	  *
	  * @param senderOpt Optionally the [[ControlMessageSender]] associated with the client.
	  * 		If the senderOpt is a `None`, the connection will be closed, after writing
	  * 		an error to the client.
	  */
	def handleHello(protocolVersion: Int, projectId: Option[Int]): Unit = {
		val readerWriterOpt = for {
			reader <- getControlMessageReader(protocolVersion)
			writer <- getControlMessageSender(protocolVersion)
		} yield (reader, writer)

		readerWriterOpt match {
			case None =>
				//tell client that they've got a bad protocol, then disconnect
				latestControlMessageSender.sendMessages(client)(Error("Invalid Protocol Version"))
				client.close
			case Some((receiver, sender)) =>

				val controlConnection = new ControlConnection(
					protocolVersion,
					client,
					receiver,
					sender,
					projectId)

				/* Calling addControlConnection triggers the connector to associate
				 * this connection with the next waiting trace object in line. The
				 * method will return the configuration message to send.
				 */
				val configMsgOpt = controlConnector.addControlConnection(controlConnection)

				configMsgOpt match {

					case None =>
						//reply with an error and close the connection
						controlConnection.send(Error("HQ is not expecting a new connection at this time"))
						controlConnection.close

					case Some(configMsg) =>
						//reply with the configuration
						controlConnection.send(configMsg)
				}
		}
	}

	/** Handle an incoming data connection after it has initiated a "data hello". A running trace will
	  * be located from the `traceRegistry` by the trace's `runId`. If no such trace is available, the
	  * client will be closed with an error. Otherwise, the client will be added to the trace as a data
	  * connection.
	  *
	  * @param runId A byte identifier which should uniquely identify the Trace that the client should
	  * be connected to.
	  */
	def handleDataHello(runId: Byte): Unit = {
		val traceFuture = traceRegistry getTrace runId

		try {
			val trace = Await.result(traceFuture, 500.millis)

			val senderAndReader = for {
				sender <- getControlMessageSender(trace.protocolVersion)
				dataParser <- getDataMessageParser(trace.protocolVersion)
			} yield (sender, dataParser)

			senderAndReader match {

				// NOTE: This (case None) should never happen. If there were no handlers,
				// the control connection should never have been made, and the
				// agent would never have gotten to the point of initiating
				// data connections. But the compiler doesn't know that, so here's
				// what would happen in that hypothetical case:
				case None =>
					// No available handlers for the trace's protocol version.
					// Send an error and close the connection.
					latestControlMessageSender.sendMessages(client)(
						Error("Failed to locate an appropriate data handler trace"))
					client.close

				case Some((controlSender, dataParser)) =>
					// turn the client into a data connection
					val dataConnection = new DataConnection(client, dataParser)

					// hand off the connection to the trace
					if (trace addDataConnection dataConnection) {
						controlSender.sendMessages(client)(DataHelloReply)
					} else {
						// Failed to add the data connection to the trace.
						// Send an error and close the connection.
						controlSender.sendMessages(client)(
							Error("Failed to associate the data connection with the trace"))
						client.close
					}
			}
		} catch {

			case e: TimeoutException =>
				// timed out waiting for the traceFuture to complete.
				// send an error and close the connection
				latestControlMessageSender.sendMessages(client)(
					Error("Data connection timed out waiting for a valid trace"))
				client.close

			case e: InterruptedException =>
				// interrupted while waiting for the traceFuture to complete.
				// send an error and close the connection
				latestControlMessageSender.sendMessages(client)(
					Error("Data connection interrupted while waiting for a valid trace"))
				client.close
		}
	}

}
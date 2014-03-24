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

package com.secdec.bytefrog.agent.control;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.secdec.bytefrog.agent.errors.ErrorHandler;
import com.secdec.bytefrog.agent.protocol.ProtocolVersion;
import com.secdec.bytefrog.common.connect.SocketConnection;
import com.secdec.bytefrog.common.message.AgentOperationMode;

/**
 * Control Controller - responsible for managing control connection
 * 
 * @author RobertF
 */
public class Controller extends Thread
{
	private final SocketConnection controlConnection;
	private final DataInputStream inStream;
	private final DataOutputStream outStream;
	private final ProtocolVersion protocol;
	private final ControlMessageProcessor messageProcessor;
	private final HeartbeatInformer heartbeatInformer;
	private Boolean isRunning = false;
	private int heartbeatInterval;

	/**
	 * Initializes a new instance of the controller. Controller is a daemon
	 * thread that may be started via <code>start()</code>.
	 * @param controlSocket the socket to use for control messages
	 * @param protocol the protocol version in use
	 * @param inStream DataInputStream for socket, if one has been created
	 * @param outStream DataOutputStream for socket, if one has been created
	 * @param heartbeatInterval interval, in milliseconds, at which to send
	 *            heartbeats
	 * @param messageHandler control message handler
	 * @param heartbeatInformer information gatherer for heartbeat messages
	 */
	public Controller(SocketConnection controlConnection, ProtocolVersion protocol,
			int heartbeatInterval, ControlMessageHandler messageHandler,
			ConfigurationHandler configHandler, HeartbeatInformer heartbeatInformer)
	{
		this.controlConnection = controlConnection;
		inStream = controlConnection.input();
		outStream = controlConnection.output();

		this.protocol = protocol;
		this.heartbeatInterval = heartbeatInterval;

		messageProcessor = protocol.getControlMessageProcessor(messageHandler, configHandler);
		this.heartbeatInformer = heartbeatInformer;

		setDaemon(true);
	}

	public void shutdown()
	{
		try
		{
			isRunning = false;
			controlConnection.close();
		}
		catch (IOException e)
		{
			ErrorHandler.handleError("IOException when stopping agent controller", e);
		}
	}

	public void sendError(String errorMessage) throws IOException
	{
		synchronized (outStream)
		{
			protocol.getMessageProtocol().writeError(outStream, errorMessage);
			outStream.flush();
		}
	}

	public void sendClassTransformed(String className) throws IOException
	{
		synchronized (outStream)
		{
			protocol.getMessageProtocol().writeClassTransformed(outStream, className);
			outStream.flush();
		}
	}

	public void sendClassTransformFailed(String className) throws IOException
	{
		synchronized (outStream)
		{
			protocol.getMessageProtocol().writeClassTransformFailed(outStream, className);
			outStream.flush();
		}
	}

	public void sendClassIgnored(String className) throws IOException
	{
		synchronized (outStream)
		{
			protocol.getMessageProtocol().writeClassIgnored(outStream, className);
			outStream.flush();
		}
	}

	public void sendDataBreak(int sequence) throws IOException
	{
		synchronized (outStream)
		{
			protocol.getMessageProtocol().writeDataBreak(outStream, sequence);
			outStream.flush();
		}
	}

	public void setHeartbeatInterval(int heartbeatInterval)
	{
		this.heartbeatInterval = heartbeatInterval;
	}

	public Boolean isRunning()
	{
		return isRunning;
	}

	private void sendHeartbeat() throws IOException
	{
		AgentOperationMode mode = heartbeatInformer.getOperationMode();
		int sendQueueSize = heartbeatInformer.getSendQueueSize();

		synchronized (outStream)
		{
			protocol.getMessageProtocol().writeHeartbeat(outStream, mode, sendQueueSize);
			outStream.flush();
		}
	}

	private void processIncomingMessage(int timeout) throws IOException, SocketException
	{
		if (timeout > 0) // sanity check, don't read with a timeout of 0
		{
			controlConnection.socket().setSoTimeout(timeout);
			try
			{
				// process a message
				messageProcessor.processIncomingMessage(inStream);
			}
			catch (SocketTimeoutException e)
			{
				// we timed out, move on...
			}
		}
	}

	@Override
	public void run()
	{
		isRunning = true;

		try
		{
			long nextHeartbeat = System.currentTimeMillis();

			while (true)
			{
				if (System.currentTimeMillis() >= nextHeartbeat)
				{
					sendHeartbeat();

					nextHeartbeat = System.currentTimeMillis() + heartbeatInterval;
				}

				// wait for a message until it's time for the next heartbeat
				int timeout = (int) (nextHeartbeat - System.currentTimeMillis());
				processIncomingMessage(timeout);
			}
		}
		catch (Exception e)
		{
			if (isRunning)
				ErrorHandler.handleError("exception in controller loop", e);
		}
		finally
		{
			isRunning = false;
		}
	}
}

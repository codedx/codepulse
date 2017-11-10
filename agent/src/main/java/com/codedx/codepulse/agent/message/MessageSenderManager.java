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

package com.codedx.codepulse.agent.message;

import java.io.IOException;
import java.net.Socket;

import com.codedx.codepulse.agent.errors.ErrorHandler;
import com.codedx.codepulse.agent.init.DataConnectionHandshake;
import com.codedx.codepulse.agent.util.SocketFactory;
import com.codedx.codepulse.agent.common.connect.Connection;
import com.codedx.codepulse.agent.common.connect.SocketConnection;
import com.codedx.codepulse.agent.common.queue.BufferPool;

/**
 * An object that manages multiple {@link PooledMessageSender} threads. Each
 * "sender" gets a dedicated socket connection, and is run on a dedicated daemon
 * thread. No connections or threads will be allocated (or started) until
 * <code>start</code> is called. Calling <code>shutdown</code> will end the
 * senders and close connections.
 * @author DylanH
 *
 */
public class MessageSenderManager
{
	private final SocketFactory connector;
	private final DataConnectionHandshake handshaker;
	private final BufferPool pool;
	private final byte runId;

	private final int numSenders;
	private final Connection[] connections;
	private final PooledMessageSender[] senders;
	private final Thread[] senderThreads;

	private boolean started = false;

	/**
	 * Creates a new MessageSenderManager
	 * @param connector A SocketFactory that will be used to initiate new socket
	 *            connections
	 * @param handshaker An object that performs a "data connection handshake"
	 *            on new socket connections
	 * @param q A MessageQueue from which each managed sender will take messages
	 * @param numSenders The number of senders to create. Each sender will get a
	 *            dedicated Socket and Thread to run with.
	 * @param runId The trace run id that will be used in the
	 *            "data connection handshake"
	 * @param maxDrained The maximum number of messages that any one sender will
	 *            take from the MessageQueue at once.
	 */
	public MessageSenderManager(SocketFactory connector, DataConnectionHandshake handshaker,
			BufferPool pool, int numSenders, byte runId)
	{
		this.connector = connector;
		this.handshaker = handshaker;
		this.numSenders = numSenders;
		this.pool = pool;
		this.runId = runId;

		connections = new Connection[numSenders];
		senders = new PooledMessageSender[numSenders];
		senderThreads = new Thread[numSenders];
	}

	/**
	 * Checks if any of the senders are currently active and sending messages.
	 * @return <code>true</code> if all senders are currently idle, or
	 *         <code>false</code> if any sender is currently active.
	 */
	public boolean isIdle()
	{
		if (started)
			for (PooledMessageSender sender : senders)
				if (sender != null && !sender.isShutdown() && !sender.isIdle())
					return false;

		return true;
	}

	/**
	 * Open and initialize sockets and threads that will be responsible for
	 * taking items out of the MessageQueue.
	 *
	 * @return <code>true</code> if this is the first call to <code>start</code>
	 *         and its actions were successful. Returns <code>false</code> if
	 *         anything went wrong.
	 */
	public boolean start()
	{
		if (started)
			return false;

		started = true;

		try
		{
			for (int i = 0; i < numSenders; i++)
			{
				Connection c = openAndHandshake();
				if (c == null)
					throw new Exception("Failed to open HQ Data connection");

				connections[i] = c;
				senders[i] = new PooledMessageSender(pool, c.output());
				senderThreads[i] = new Thread(senders[i]);
				senderThreads[i].setDaemon(true);
			}
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("Failed to start the MessageSenderManager", e);
			return false;
		}

		for (Thread t : senderThreads)
		{
			t.start();
		}
		return true;
	}

	/**
	 * Shut down all of the senders that this object manages.
	 */
	public void shutdown()
	{
		for (PooledMessageSender sender : senders)
		{
			if (sender != null)
				sender.shutdown();
		}

		// interrupt and wait for threads to stop
		for (Thread t : senderThreads)
			if (t != null)
				t.interrupt();

		for (Thread t : senderThreads)
		{
			try
			{
				if (t != null)
				{
					t.join();
				}
			}
			catch (InterruptedException ex)
			{
			}
		}
	}

	/**
	 * Opens a new HQ Socket connection and attempts to perform the "Data"
	 * handshake.
	 *
	 * @return The opened socket on success. <code>null</code> on failure.
	 * @throws SecurityException
	 * @throws IOException
	 */
	private Connection openAndHandshake() throws SecurityException, IOException
	{
		Socket s = connector.connect();
		Connection c = new SocketConnection(s, false, true);
		boolean success = false;
		try
		{
			// DataOutputStream sOut = new
			// DataOutputStream(s.getOutputStream());
			// DataInputStream sIn = new DataInputStream(s.getInputStream());

			success = handshaker.performHandshake(runId, c);
		}
		finally
		{
			// ensure that the socket is closed if the handshake didn't work
			if (!success)
				c.close();
		}

		if (success)
			return c;
		else
			return null;
	}
}

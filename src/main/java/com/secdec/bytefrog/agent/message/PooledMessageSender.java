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

package com.secdec.bytefrog.agent.message;

import java.io.IOException;
import java.io.OutputStream;

import com.secdec.bytefrog.agent.errors.ErrorHandler;
import com.secdec.bytefrog.common.queue.BufferPool;
import com.secdec.bytefrog.common.queue.DataBufferOutputStream;

/**
 * A Runnable that will repeatedly attempt to call
 * {@link BufferPool#acquireForReading()} on the given <code>pool</code>,
 * sending the entire contents of the acquired buffer to the given OutputStream
 * <code>out</code>, before clearing it and releasing it back to the pool.
 * @author DylanH
 */
public class PooledMessageSender implements Runnable
{

	private final OutputStream out;
	private final BufferPool pool;
	private volatile boolean isShutdown = false;
	private volatile boolean idle = false;

	public PooledMessageSender(BufferPool pool, OutputStream out)
	{
		this.pool = pool;
		this.out = out;
	}

	public boolean isIdle()
	{
		return idle;
	}

	public void shutdown()
	{
		isShutdown = true;
	}

	public boolean isShutdown()
	{
		return this.isShutdown;
	}

	@Override
	public void run()
	{
		try
		{
			while (!isShutdown)
			{
				doSend();
			}
		}
		catch (Exception e)
		{
			ErrorHandler
					.handleError("An unforseen error occurred while running a MessageSender", e);
		}
		finally
		{
			try
			{
				out.close();
				shutdown();
			}
			catch (IOException e)
			{
			}
		}
	}

	/**
	 * @return <code>true</code> if any items were sent, or <code>false</code>
	 *         if nothing was sent (i.e. if the queue was empty)
	 */
	private boolean doSend()
	{
		idle = true;
		// we are "idle" when we block during the acquire
		DataBufferOutputStream buffer;
		try
		{
			buffer = pool.acquireForReading();
		}
		catch (InterruptedException e)
		{
			return false;
		}
		idle = false;

		try
		{
			// attempt to write each buffer to the output stream
			buffer.writeTo(out);
		}
		catch (IOException e)
		{
			// IO errors might happen... report them
			ErrorHandler.handleError("Failed to write data buffer", e);
		}
		finally
		{
			buffer.reset();
			pool.release(buffer);
		}

		return true;
	}
}

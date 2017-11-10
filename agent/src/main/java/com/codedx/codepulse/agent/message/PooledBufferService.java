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

import com.codedx.codepulse.agent.common.queue.BufferPool;
import com.codedx.codepulse.agent.common.queue.DataBufferOutputStream;

/**
 * A BufferService implementation that obtains and sends buffers from a
 * BufferPool instance. Each call to <code>obtain</code> will use
 * {@link BufferPool#acquireForWriting()}, and will retry up to a certain
 * maximum number of retries, if it is interrupted while waiting for an
 * available buffer. Sending a buffer is equivalent to releasing it back to the
 * BufferPool.
 *
 * @author DylanH
 */
public class PooledBufferService extends BufferService
{
	private final BufferPool pool;
	private final int maxObtainRetries;

	public PooledBufferService(BufferPool pool, int maxObtainRetries)
	{
		this.pool = pool;
		this.maxObtainRetries = maxObtainRetries;
	}

	@Override
	public void setSuspended(boolean suspended)
	{
		// we need to keep pool in sync with the suspension, so any existing
		// waits will be cancelled and not cause a deadlock
		pool.setWriteDisabled(suspended);

		super.setSuspended(suspended);
	}

	/**
	 * Obtains a data buffer from the backing MessageQueue's
	 * <code>freeBuffers</code>. If the <code>take</code> operation is
	 * interrupted while blocking, this operation will retry (up to a certain
	 * maximum number of retries) calling <code>take</code>, and re-interrupt
	 * the thread before returning.
	 */
	@Override
	protected DataBufferOutputStream innerObtain() throws FailedToObtainBufferException
	{
		boolean interrupted = false;
		for (int i = 0; i < maxObtainRetries; i++)
		{
			try
			{
				// checks and clears the 'interrupted' flag, so that the
				// subsequent "take" operation doesn't die immediately
				interrupted = Thread.interrupted();

				// If the thread is re-interrupted while blocking on "take",
				// "take" will throw an InterruptedException. We need to catch
				// that and remember that we got interrupted so that we can
				// re-interrupt before returning.
				return pool.acquireForWriting();
			}
			catch (InterruptedException e)
			{
				// remember that we were interrupted
				interrupted = true;
			}
			finally
			{
				// re-interrupt if we were interrupted before
				if (interrupted)
					Thread.currentThread().interrupt();
			}
		}
		// at this point, we've retried too many times, so the method failed.
		throw new FailedToObtainBufferException("Too many retries");
	}

	/**
	 * Sends a data buffer to the backing MessageQueue's
	 * <code>filledBuffers</code>. If the <code>put</code> operation is
	 * interrupted whie blocking, this operation will retry (up to a certain
	 * maximum number of retries) calling <code>put</code>, and re-interrupt the
	 * thread before returning.
	 */
	@Override
	protected void innerSend(DataBufferOutputStream dataBuffer) throws FailedToSendBufferException
	{
		pool.release(dataBuffer);
	}

}

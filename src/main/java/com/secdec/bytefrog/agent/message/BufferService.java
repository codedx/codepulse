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

import com.secdec.bytefrog.agent.control.ModeChangeListener;
import com.secdec.bytefrog.common.message.AgentOperationMode;
import com.secdec.bytefrog.common.queue.BufferPool;
import com.secdec.bytefrog.common.queue.DataBufferOutputStream;

/**
 * An object that is responsible for getting and sending data buffers, to be
 * used in conjunction with a {@link MessageQueue} or [@link {@link BufferPool}.
 * A <code>BufferService</code> can be paused and suspended at will. Pausing
 * will cause all <code>obtain</code> operations to block until the service is
 * unpaused. Suspending will cause all <code>obtain</code> methods to return
 * null.
 * 
 * {@link #innerObtain()} and {@link #innerSend(DataBufferOutputStream)} are
 * left to subclasses to implement.
 * 
 * @author dylanh
 */
public abstract class BufferService
{
	protected abstract DataBufferOutputStream innerObtain() throws FailedToObtainBufferException;

	protected abstract void innerSend(DataBufferOutputStream buffer)
			throws FailedToSendBufferException;

	private final ModeListener modeListener = new ModeListener();

	private final Object pauseObj = new Object();
	private volatile boolean paused = false;
	private volatile boolean suspended = false;

	public void setPaused(boolean paused)
	{
		this.paused = paused;
		if (!paused)
		{
			synchronized (pauseObj)
			{
				pauseObj.notifyAll();
			}
		}
	}

	public void setSuspended(boolean suspended)
	{
		this.suspended = suspended;
	}

	public DataBufferOutputStream obtainBuffer() throws FailedToObtainBufferException
	{
		blockWhilePaused();
		if (suspended)
		{
			return null;
		}
		else
		{
			return innerObtain();
		}
	}

	public void sendBuffer(DataBufferOutputStream buffer) throws FailedToSendBufferException
	{
		if (buffer != null)
			innerSend(buffer);
	}

	private void blockWhilePaused()
	{
		// get and clear the "interrupted" flag in one shot
		boolean interrupted = Thread.interrupted();

		while (paused)
		{
			try
			{
				synchronized (pauseObj)
				{
					pauseObj.wait();
				}
			}
			catch (InterruptedException e)
			{
				// continue waiting, but set the "interrupted" flag
				interrupted = true;
			}
		}

		// re-set the "interrupted" flag if needed
		if (interrupted)
			Thread.currentThread().interrupt();
	}

	private class ModeListener implements ModeChangeListener
	{
		@Override
		public void onModeChange(AgentOperationMode oldMode, AgentOperationMode newMode)
		{
			switch (newMode)
			{
			case Paused:
				setPaused(true);
				break;

			case Suspended:
				setSuspended(true);
				break;

			case Tracing:
				switch (oldMode)
				{
				case Paused:
					setPaused(false);
					break;

				case Suspended:
					setSuspended(false);
					break;

				default:
					break;
				}
				break;

			case Shutdown:
				if (paused)
					setPaused(false);
				setSuspended(true);
				break;

			default:
				break;
			}
		}
	}

	public ModeChangeListener getModeChangeListener()
	{
		return modeListener;
	}
}

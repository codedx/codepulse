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

package com.codedx.codepulse.agent.common.queue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * An object that manages a collection of {@link DataBufferOutputStream}s,
 * allowing threads to acquire and release buffers in a thread-safe manner.
 * Buffer acquisition has two modes, "for reading" and "for writing". Acquiring
 * a buffer for reading will have different internal behavior than acquiring a
 * buffer for writing, but each operation will block until a buffer is
 * available, or until the current thread is interrupted.
 *
 * Internally, a BufferPool uses three {@link ConcurrentLinkedQueue}s, with a
 * {@link Semaphore} to control access. Using this approach, the pool achieves
 * high availability, even for a large number of producer and consumer threads.
 * @author DylanH
 */
public class BufferPool
{
	private final Semaphore emptySem;
	private final Semaphore partialSem;
	private final Semaphore fullSem;

	private final ConcurrentLinkedQueue<DataBufferOutputStream> emptyBuffers;
	private final ConcurrentLinkedQueue<DataBufferOutputStream> partialBuffers;
	private final ConcurrentLinkedQueue<DataBufferOutputStream> fullBuffers;

	private final int fullThreshold;
	private final int totalNumBuffers;

	private volatile boolean writeDisabled = false;

	/**
	 * Constructs a new BufferPool with the given number of initially-empty
	 * buffers, where each buffer has a specified length.
	 *
	 * In terms of memory consumption, a soft-limit is set on the number of
	 * bytes allocated by this BufferPool, equal to
	 * <code>numBuffers * bufferLengthHint</code>. Once any given buffer reaches
	 * a "fullness" factor of at least 0.9, it will be considered "full", and
	 * will only be returned for reading. This helps limit the possibility that
	 * a buffer will exceed its internal capacity and need to be re-allocated. A
	 * buffer may still automatically re-allocate itself when a single writer
	 * thread adds a large number of bytes to that buffer before releasing it.
	 * Because of this, client code is encouraged to write data in relatively
	 * small chunks of less than 10% of the bufferLength at a time.
	 *
	 * @param numBuffers The number of initially-empty buffers. Note that the
	 *            number of buffers given will directly limit the number of
	 *            threads that may access the pool concurrently.
	 * @param bufferLengthHint The number of bytes initially allocated to each
	 *            buffer. Note that this number is not a hard limit, but if a
	 *            buffer grows beyond this threshold, it will not be available
	 *            for further writing until a reader thread clears and releases
	 *            it.
	 */
	public BufferPool(int numBuffers, int bufferLengthHint)
	{
		this.fullThreshold = (int) (bufferLengthHint * 0.9);
		this.totalNumBuffers = numBuffers;

		// as per the docs, fairness does nothing with tryAcquire. You'd have to
		// tryAcquire with a timeout of 0 seconds for fairness to come into
		// play.
		emptySem = new Semaphore(numBuffers);
		partialSem = new Semaphore(0);
		fullSem = new Semaphore(0);

		emptyBuffers = new ConcurrentLinkedQueue<DataBufferOutputStream>();
		partialBuffers = new ConcurrentLinkedQueue<DataBufferOutputStream>();
		fullBuffers = new ConcurrentLinkedQueue<DataBufferOutputStream>();

		for (int i = 0; i < numBuffers; i++)
		{
			emptyBuffers.offer(new DataBufferOutputStream(bufferLengthHint));
		}
	}

	/**
	 * Acquires a Buffer from the pool, for the purpose of adding new data. This
	 * method will prioritize partially-filled buffers over empty buffers, and
	 * will never return a buffer whose current size is greater than the
	 * <code>bufferLengthHint</code> that was given at constructor time. If no
	 * buffers are currently available, this method will continue retrying until
	 * one becomes available, or the thread is interrupted.
	 *
	 * @return A new Buffer from the pool, ready to have new data written to it.
	 *         If writes are disabled, this method will return null.
	 * @throws InterruptedException if the thread is interrupted while waiting
	 *             for a buffer.
	 */
	public DataBufferOutputStream acquireForWriting() throws InterruptedException
	{
		// we may have to try multiple times to "acquire" a permit.
		// when we fail to acquire, there will be different effects
		// depending on how high the "tryCount" is.
		int tryCount = 0;

		// the only way to exit is to return successfully or be interrupted
		while (true)
		{
			if (writeDisabled)
				return null;

			// try to get a buffer from the "partially filled" ones first
			if (partialSem.tryAcquire())
			{
				return partialBuffers.poll();
			}
			// second best is to get an empty buffer
			else if (emptySem.tryAcquire())
			{
				return emptyBuffers.poll();
			}
			// if both of those failed, "wait" and try again
			else
				waitCycle(tryCount++);
		}
	}

	/**
	 * Equivalent to {@link #acquireForReading(boolean)} with an argument of
	 * <code>false</code>
	 * @return A Buffer from the pool, for the purpose of extracting data from
	 *         it.
	 * @throws InterruptedException if the thread is interrupted while waiting
	 *             for a buffer.
	 */
	public DataBufferOutputStream acquireForReading() throws InterruptedException
	{
		return acquireForReading(false);
	}

	/**
	 * Acquires a buffer from the pool, for the purpose of reading data from it.
	 * Readers should call <code>resultingBuffer.reset()</code> before calling
	 * {@link #release(DataBufferOutputStream)}. This method will strongly
	 * prioritize "full" buffers over partially filled buffers. If a "full"
	 * buffer is not immediately available, it will wait for one to become
	 * available for some time before attempting to obtain a partially filled
	 * buffer. This method will continue retrying to obtain a buffer until it
	 * finally succeeds, or the thread is interrupted.
	 * @param greedy If <code>true</code>, this method will not wait while
	 *            partially-filled buffers are available. Otherwise, it will
	 *            hold out for a full buffer, for a short time.
	 * @return A Buffer from the pool, for the purpose of extracting data from
	 *         it.
	 * @throws InterruptedException if the thread is interrupted while waiting
	 *             for a buffer.
	 */
	public DataBufferOutputStream acquireForReading(boolean greedy) throws InterruptedException
	{
		// we may have to try multiple times to "acquire" a permit.
		// when we fail to acquire, there will be different effects
		// depending on how high the "tryCount" is.
		int tryCount = 0;

		// the only way to exit is to return successfully or be interrupted
		while (true)
		{
			// first try to acquire a permit for one of the "full" buffers
			if (fullSem.tryAcquire())
			{
				return fullBuffers.poll();
			}
			// second choice is to get a "partially filled" buffer
			// Note: unless the greedy flag is set, don't start looking for
			// partial buffers until we've failed to find a full buffer for many
			// retries. We don't want readers to be so greedy that no buffer
			// gets a chance to fill up.
			// When we're not being greedy, we wait roughly 80-100ms (or a
			// little more, depending on timer resolution) before sending
			// partial buffers.
			else if ((greedy || tryCount > 100) && partialSem.tryAcquire())
			{
				return partialBuffers.poll();
			}
			// otherwise, wait
			else
				waitCycle(tryCount++);
		}
	}

	/**
	 * Internal helper to handle spinning/sleeping while waiting for a buffer.
	 * Yields the current thread for 20 cycles (somewhere between 0 and 20 ms),
	 * then sleeps after.
	 * @param cycleCount the current count of cycles we've been waiting
	 */
	private void waitCycle(int cycleCount) throws InterruptedException
	{
		if (cycleCount < 20) // yield and spin for a little while
			Thread.yield();
		else
			// after a short while, start sleeping
			Thread.sleep(1);
	}

	/**
	 * Returns the given <code>buffer</code> to the pool. Depending on the
	 * current "size" of the buffer, it will be placed in a different queue (one
	 * of the "empty", "partial", or "full" queues). The buffer becomes
	 * available for future acquisition. Note that if the released buffer is
	 * "empty", it will not be available for reading; if it is "full", it will
	 * not be available for writing. Partially-filled buffers are made available
	 * for either purpose.
	 * @param buffer The buffer to return to the pool.
	 */
	public void release(DataBufferOutputStream buffer)
	{
		// depending on the buffer's current "size", it will be placed in a
		// different queue.
		int size = buffer.size();
		if (size == 0)
		{
			// size == 0 means the buffer was empty
			emptyBuffers.offer(buffer);
			emptySem.release();
		}
		else if (size < fullThreshold)
		{
			// the buffer is partially full
			partialBuffers.offer(buffer);
			partialSem.release();
		}
		else
		{
			// the buffer is full
			fullBuffers.offer(buffer);
			fullSem.release();
		}
	}

	/**
	 * @return The number of currently-available buffers in either of the
	 *         "partially-filled" or "full" queues.
	 */
	public int numReadableBuffers()
	{
		return partialSem.availablePermits() + fullSem.availablePermits();
	}

	/**
	 * Returns the number of writable (non-full) buffers.
	 * @return the number of writable (non-full) buffers
	 */
	public int numWritableBuffers()
	{
		return emptySem.availablePermits() + partialSem.availablePermits();
	}

	/**
	 * Checks if the buffers from this pool are *all* empty, and that they have
	 * all been released.
	 * @return <code>true</code> if all of this pool's buffers are currently
	 *         empty, and no buffers are currently marked as partial or full.
	 */
	public boolean isEmpty()
	{
		return emptySem.availablePermits() == totalNumBuffers && partialSem.availablePermits() == 0
				&& fullSem.availablePermits() == 0;
	}

	/**
	 * Enables or disables the returning of writeable buffers. When this is set
	 * to true, acquireForWriting() will return null rather than a valid value.
	 * Disabling writing will cause any pending waits for an available to buffer
	 * to return null as well.
	 * @param writeDisabled whether or not writing is disabled
	 */
	public void setWriteDisabled(boolean writeDisabled)
	{
		this.writeDisabled = writeDisabled;
	}
}

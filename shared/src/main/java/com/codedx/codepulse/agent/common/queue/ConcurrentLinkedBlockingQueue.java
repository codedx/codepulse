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

package com.codedx.codepulse.agent.common.queue;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A BlockingQueue implementation backed by ConcurrentLinkedQueue.
 * @author RobertF
 *
 * @param <E>
 */
public class ConcurrentLinkedBlockingQueue<E> implements BlockingQueue<E>
{
	private final AtomicInteger size = new AtomicInteger(0);
	private final ConcurrentLinkedQueue<E> inner = new ConcurrentLinkedQueue<E>();

	/**
	 * Inner helper for initially spinning, then sleeping.
	 * @param cycle the cycle we're up to in the wait
	 * @throws InterruptedException
	 */
	private void waitCycle(int cycle) throws InterruptedException
	{
		if (cycle < 2048)
			Thread.yield();
		else if (cycle < 8192) // sleep minimum for ~6 seconds
			Thread.sleep(1);
		else
			Thread.sleep(100); // sleep a little longer afterwards
	}

	@Override
	public E element()
	{
		return inner.element();
	}

	@Override
	public E peek()
	{
		return inner.peek();
	}

	@Override
	public E poll()
	{
		E val = inner.poll();
		if (val != null)
			size.decrementAndGet();
		return val;
	}

	@Override
	public E remove()
	{
		// in this order, so if inner throws, we don't change size
		E e = inner.remove();
		size.decrementAndGet();
		return e;
	}

	@Override
	public boolean addAll(Collection<? extends E> c)
	{
		if (inner.addAll(c))
		{
			size.addAndGet(c.size());
			return true;
		}
		return false;
	}

	@Override
	public void clear()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEmpty()
	{
		return inner.isEmpty();
	}

	@Override
	public Iterator<E> iterator()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int size()
	{
		return size.get();
	}

	@Override
	public Object[] toArray()
	{
		return inner.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a)
	{
		return inner.toArray(a);
	}

	@Override
	public boolean add(E e)
	{
		// in this order, so if inner throws, we don't change size
		inner.add(e);
		size.incrementAndGet();
		return true;
	}

	@Override
	public boolean contains(Object o)
	{
		return inner.contains(o);
	}

	@Override
	public int drainTo(Collection<? super E> c)
	{
		if (c == null)
			throw new NullPointerException();
		if (c == this)
			throw new IllegalArgumentException();

		return drainTo(c, size());
	}

	@Override
	public int drainTo(Collection<? super E> c, int maxElements)
	{
		if (c == null)
			throw new NullPointerException();
		if (c == this)
			throw new IllegalArgumentException();
		if (maxElements <= 0)
			return 0;

		int ct = 0;
		for (ct = 0; ct < maxElements; ct++)
		{
			E e = poll();
			if (e == null)
				break;
			else
				c.add(e);
		}
		return ct;
	}

	@Override
	public boolean offer(E e)
	{
		if (inner.offer(e))
		{
			size.incrementAndGet();
			return true;
		}

		return false;
	}

	@Override
	public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException
	{
		return offer(e); // ConcurrentLinkedQueue will never block
	}

	@Override
	public E poll(long timeout, TimeUnit unit) throws InterruptedException
	{
		E e = null;
		int i = 0;
		long waitEnd = System.currentTimeMillis() + unit.toMillis(timeout);

		while ((e = poll()) == null && System.currentTimeMillis() <= waitEnd)
			waitCycle(++i);

		return e;
	}

	@Override
	public void put(E e) throws InterruptedException
	{
		if (inner.add(e))
			size.incrementAndGet();
	}

	@Override
	public int remainingCapacity()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public E take() throws InterruptedException
	{
		E e;
		int i = 0;

		while ((e = poll()) == null)
			waitCycle(++i);

		return e;
	}
}

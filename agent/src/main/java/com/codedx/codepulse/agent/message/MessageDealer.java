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

package com.codedx.codepulse.agent.message;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.codedx.bytefrog.instrumentation.LineLevelMapper;
import com.codedx.bytefrog.instrumentation.id.*;

import com.codedx.codepulse.agent.common.message.MessageProtocol;
import com.codedx.codepulse.agent.common.message.NotSupportedException;
import com.codedx.codepulse.agent.common.queue.DataBufferOutputStream;

/**
 * An object that is responsible for sending data messages according to
 * protocol. Messages are written to buffers which are provided by a
 * {@link BufferService}, then sent via the same BufferService. For events that
 * require mapped ids for the current thread and method signature, those ids
 * (along with the appropriate secondary "map" events) will be automatically
 * generated. The time of MessageFactory's construction will be saved, and used
 * to calculate the "relative timestamp" for each event that requires one.
 *
 * @author dylanh
 */
public class MessageDealer
{
	private final MessageProtocol messageProtocol;
	private final BufferService bufferService;

	private final long startTime = System.currentTimeMillis();
	private final ThreadId threadIdMapper = new ThreadId();
	private final Sequencer sequencer = new Sequencer();

	private final MethodIdAdapter methodIdAdapter;

	private static final int unavailableSourceLocationId = -1;

	/**
	 *
	 * @param messageProtocol
	 * @param bufferService
	 */
	public MessageDealer(MessageProtocol messageProtocol, BufferService bufferService, ClassIdentifier classIdentifier, MethodIdentifier methodIdentifier)
	{
		this.messageProtocol = messageProtocol;
		this.bufferService = bufferService;

		methodIdAdapter = new MethodIdAdapter(classIdentifier, methodIdentifier);
	}

	// just a helper method, used internally
	protected int getTimeOffset()
	{
		return (int) (System.currentTimeMillis() - startTime);
	}

	/**
	 * Observes (returns) the next sequencer ID, without incrementing.
	 *
	 * @returns the next sequencer ID
	 */
	public int getCurrentSequence()
	{
		return sequencer.observeSequence();
	}

	// ===============================
	// API METHODS:
	// ===============================

	/**
	 * METHOD ENTRY (EVENT) MESSAGE
	 *
	 * @param methodId
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMethodEntry(int methodId) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();
				int threadId = threadIdMapper.getCurrent();
				methodIdAdapter.mark(methodId, buffer);
				messageProtocol.writeMethodEntry(buffer, timestamp, sequencer.getSequence(),
						methodId, threadId);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	/**
	 * METHOD EXIT (EVENT) MESSAGE
	 *
	 * @param methodId
	 * @param exThrown
	 * @throws IOException
	 * @throws FailedToObtainBufferException
	 * @throws FailedToSendBufferException
	 */
	public void sendMethodExit(int methodId, boolean exThrown) throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();
				int threadId = threadIdMapper.getCurrent();
				methodIdAdapter.mark(methodId, buffer);
				messageProtocol.writeMethodExit(buffer, timestamp, sequencer.getSequence(),
						methodId, exThrown, threadId);
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	public void recordLineLevelTrace(int methodId, int startLine, int endLine, java.util.BitSet lineMap)  throws IOException,
			FailedToObtainBufferException, FailedToSendBufferException, NotSupportedException
	{
		DataBufferOutputStream buffer = bufferService.obtainBuffer();
		if (buffer != null)
		{
			boolean wrote = false;
			try
			{
				int timestamp = getTimeOffset();

				int threadId = threadIdMapper.getCurrent();
				for (int i = lineMap.nextSetBit(0); i >= 0; i = lineMap.nextSetBit(i+1)) {
					int sourceLocationId = methodIdAdapter.markSourceLocation(methodId, startLine+i, startLine+i, buffer);
					if (sourceLocationId != unavailableSourceLocationId) {
						messageProtocol.writeMethodVisit(buffer, timestamp, sequencer.getSequence(), methodId, sourceLocationId, threadId);
					}
				}
				wrote = true;
			}
			finally
			{
				if (!wrote)
					buffer.reset();
				bufferService.sendBuffer(buffer);
			}
		}
	}

	private class MethodIdAdapter
	{
		private final ClassIdentifier classIdentifier;
		private final MethodIdentifier methodIdentifier;

		private final ConcurrentMap<Integer, Boolean> observedIds = new ConcurrentHashMap<Integer, Boolean>();

		private final ConcurrentMap<Integer, Integer> sourceLocationCounts = new ConcurrentHashMap<>();

		private final AtomicInteger nextSourceLocationId = new AtomicInteger();
		protected final ConcurrentHashMap<String, Integer> sourceLocationMap = new ConcurrentHashMap<>();

		public MethodIdAdapter(ClassIdentifier classIdentifier, MethodIdentifier methodIdentifier)
		{
			this.classIdentifier = classIdentifier;
			this.methodIdentifier = methodIdentifier;
		}

		public int markSourceLocation(int methodId, int startLine, int endLine, DataBufferOutputStream buffer)
				throws IOException, NotSupportedException, FailedToObtainBufferException, FailedToSendBufferException {

			mark(methodId, buffer);

			String key = String.format("%d-%d-%d", methodId, startLine, endLine);
			Integer id = sourceLocationMap.get(key);
			if (id != null)
			{
				return id;
			}

			int classId = methodIdentifier.get(methodId).getClassId();

			ClassIdentifier.ClassInformation classInformation = classIdentifier.get(classId);
			LineLevelMapper llm = classInformation.getLineLevelMapper();
			if (llm != null) {

				Boolean sourceLocationCountsMessageSent = sourceLocationCounts.containsKey(classId);
				if (!sourceLocationCountsMessageSent) {
					HashSet<String> mappedLocations = new HashSet<>();

					BitSet lineNumbers = classInformation.getLineNumbers();
					for (int l = lineNumbers.nextSetBit(0); l >= 0; l = lineNumbers.nextSetBit(l + 1)) {
						BitSet b = new BitSet();
						b.set(0);
						LineLevelMapper.MappedCoverage mappedCoverage[] = llm.map(l, b);
						if (mappedCoverage != null) {
							StringBuilder s = new StringBuilder();
							for (LineLevelMapper.MappedCoverage mappedCoverageItem : mappedCoverage) {
								for (int q = mappedCoverageItem.lines.nextSetBit(0); q >= 0; q = mappedCoverageItem.lines.nextSetBit(q + 1)) {
									s.append(mappedCoverageItem.startLine + q);
									s.append("; ");
								}
							}
							mappedLocations.add(s.toString());
						}
					}

					int mappingsCount = mappedLocations.size();
					messageProtocol.writeSourceLocationCount(buffer, methodId, mappingsCount);
					sourceLocationCounts.put(classId, mappingsCount);
				}

				BitSet bitSet = new BitSet();
				bitSet.set(0, endLine - startLine + 1);

				LineLevelMapper.MappedCoverage mappedCoverage[] = llm.map(startLine, bitSet);
				if (mappedCoverage == null) {
					return unavailableSourceLocationId;
				}

				int newStartLine = Integer.MAX_VALUE;
				int newEndLine = Integer.MIN_VALUE;
				for (LineLevelMapper.MappedCoverage mappedCoverageItem: mappedCoverage){
					for (int l = mappedCoverageItem.lines.nextSetBit(0); l >= 0; l = mappedCoverageItem.lines.nextSetBit(l + 1)) {
						newStartLine = l < newStartLine ? l : newStartLine;
						newEndLine = l > newEndLine ? l : newEndLine;
					}
				}

				// adjust for zero-based line offset
				startLine = newStartLine + 1;
				endLine = newEndLine + 1;
			}

			Integer newId = nextSourceLocationId.getAndIncrement();
			id = sourceLocationMap.putIfAbsent(key, newId);

			if (id == null)
			{
				short ignored = -1;
				messageProtocol.writeMapSourceLocation(buffer, newId, methodId, startLine, endLine, ignored, ignored);
				return newId;
			}
			return id;
		}

		public void mark(int methodId, DataBufferOutputStream buffer) throws IOException, FailedToObtainBufferException, FailedToSendBufferException
		{
			Boolean seen = observedIds.putIfAbsent(methodId, true);

			if (seen == null || !seen)
			{
				MethodIdentifier.MethodInformation m = methodIdentifier.get(methodId);
				ClassIdentifier.ClassInformation c = classIdentifier.get(m.getClassId());

				String signature = c.getName() + "." + m.getName() + ";" + m.getAccess() + ";" + m.getDescriptor();
				messageProtocol.writeMapMethodSignature(buffer, methodId, signature);
			}
		}
	}

	/**
	 * Creates a monotonically-incrementing unique id for each thread. The id
	 * for the currently-running thread is available via {@link #getCurrent()}
	 *
	 * @author dylanh
	 *
	 */
	private class ThreadId
	{
		/**
		 * Creates a new, unique id for each thread, on demand.
		 */
		private final ThreadLocal<Integer> threadId = new ThreadLocal<Integer>()
		{
			private final AtomicInteger uniqueId = new AtomicInteger(0);

			@Override
			protected Integer initialValue()
			{
				return uniqueId.getAndIncrement();
			};
		};

		/**
		 * Per-thread storage of whether or not a thread has a `threadId`
		 * association. The `getCurrent` method will perform a check and update
		 * using this.
		 */
		private final ThreadLocal<Boolean> threadHasId = new ThreadLocal<Boolean>()
		{
			@Override
			protected Boolean initialValue()
			{
				return false;
			};
		};

		/**
		 * Stores the latest known name of a thread. Updated by `getCurrent`.
		 */
		private final ThreadLocal<String> threadName = new ThreadLocal<String>()
		{
			@Override
			protected String initialValue()
			{
				return Thread.currentThread().getName();
			};
		};

		/**
		 * Get the unique id of the currently-running thread.
		 *
		 * @return The unique id for the currently-running thread.
		 * @throws InterruptedException
		 * @throws IOException
		 * @throws FailedToSendBufferException
		 * @throws FailedToObtainBufferException
		 */
		public int getCurrent() throws IOException, FailedToObtainBufferException,
				FailedToSendBufferException
		{
			int id = threadId.get();

			// check if the id is "new"
			if (!threadHasId.get())
			{
				threadHasId.set(true);
			}

			// check if the name has changed
			String oldName = threadName.get();
			if (oldName == null)
				oldName = "";
			String nowName = Thread.currentThread().getName();
			if (nowName == null)
				nowName = "";

			if (!nowName.equals(oldName))
			{
				threadName.set(nowName);
			}

			return id;
		}
	}

	/**
	 * Provides an incrementing sequence counter for events.
	 *
	 * @author RobertF
	 *
	 */
	private class Sequencer
	{
		private final AtomicInteger sequenceId = new AtomicInteger();

		/**
		 * Get a new (unique) sequence identifier.
		 *
		 * @return current sequence value
		 */
		public int getSequence()
		{
			return sequenceId.getAndIncrement();
		}

		/**
		 * Observes the current sequence identifier, without modifying it.
		 *
		 * @return the next sequence value
		 */
		public int observeSequence()
		{
			return sequenceId.get();
		}
	}
}

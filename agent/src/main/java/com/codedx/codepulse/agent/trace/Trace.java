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

package com.codedx.codepulse.agent.trace;

import com.codedx.codepulse.agent.trace.TraceDataCollector;

/**
 * Helper object that performs the actual work in handling trace data. Called by
 * the injected bytecode.
 *
 * The intended usage of this class is that third party classes will be modified
 * to call the static "event" methods on this class via BCI.
 *
 * @author RobertF, DylanH
 */
public class Trace
{
	private volatile static TraceDataCollector traceDataCollector;

	private Trace()
	{
	}

	/**
	 * Sets the trace data collector instance to be used.
	 * @param traceDataCollector the trace data collector instance to be used
	 */
	public static void setTraceDataCollector(TraceDataCollector traceDataCollector)
	{
		Trace.traceDataCollector = traceDataCollector;
	}

	public static void methodEnter(int methodId)
	{
		traceDataCollector.methodEntry(methodId);
	}

	public static void methodExit(int methodId, boolean exceptionThrown)
	{
		if (exceptionThrown)
			traceDataCollector.bubbleException("???", methodId);
		else
			traceDataCollector.methodExit(methodId, -1);
	}

	public static void recordLineLevelTrace(int methodId, int startLine, int endLine, java.util.BitSet lineMap)
	{
	}
}
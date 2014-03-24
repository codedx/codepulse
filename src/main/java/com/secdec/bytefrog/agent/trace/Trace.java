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

package com.secdec.bytefrog.agent.trace;

import com.secdec.bytefrog.agent.TraceDataCollector;

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

	/**
	 * Notifies the Agent system that the Target application is entering a
	 * method.
	 * @param methodSignature The JVM signature of the method being entered
	 */
	public static void methodEntry(String methodSignature)
	{
		traceDataCollector.methodEntry(methodSignature);
	}

	/**
	 * Notifies the Agent system that the Target application is exiting a
	 * method. Exit is by a normal return.
	 * @param methodSignature The JVM signature of the currently-executing
	 *            method.
	 * @param sourceLine the line number, if available, of the exit
	 */
	public static void methodExit(String methodSignature, int sourceLine)
	{
		traceDataCollector.methodExit(methodSignature, sourceLine);
	}

	/**
	 * Notifies the Agent system that the Target application has thrown an
	 * exception. A bubble message will follow if the exception was unhandled.
	 * @param exception The exception thrown.
	 * @param methodSignature The JVM signature of the currently-executing
	 *            method.
	 * @param sourceLine the line number, if available, of the exit
	 */
	public static void methodThrow(Throwable exception, String methodSignature, int sourceLine)
	{
		traceDataCollector.exception(exception.getClass().getName(), methodSignature, sourceLine);
	}

	/**
	 * Notifies the Agent system that the Target application has bubbled an
	 * exception out of a method.
	 * @param exception The exception bubbled.
	 * @param methodSignature The JVM signature of the method that bubbled the
	 *            exception.
	 */
	public static void methodBubble(Throwable exception, String methodSignature)
	{
		traceDataCollector.bubbleException(exception.getClass().getName(), methodSignature);
	}

	/**
	 * Notifies the Agent system that the Target application wants to send a
	 * marker message to HQ. Note that the marker message is not part of normal
	 * tracing operation; it is actually a way for custom tracer applications to
	 * send arbitrary event messages.
	 * 
	 * @param key The marker's key
	 * @param value The marker's value
	 */
	public static void marker(String key, String value)
	{
		traceDataCollector.marker(key, value);
	}
}

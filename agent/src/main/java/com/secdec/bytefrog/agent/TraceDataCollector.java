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

package com.secdec.bytefrog.agent;

/**
 * Interface for trace data collectors.
 * @author RobertF
 */
public interface TraceDataCollector
{
	/**
	 * Reports a method entry.
	 * @param methodSig method signature for the method being entered
	 */
	void methodEntry(String methodSig);

	/**
	 * Reports a method exit.
	 * @param methodSig method signature for the method being exited
	 * @param sourceLine the line number where the method exit occurred
	 */
	void methodExit(String methodSig, int sourceLine);

	/**
	 * Reports a exception.
	 * @param exception the fully qualified name of the exception thrown
	 * @param methodSig method signature for the method throwing the exception
	 * @param sourceLine the line number where the exception was thrown
	 */
	void exception(String exception, String methodSig, int sourceLine);

	/**
	 * Reports a bubbled exception.
	 * @param exception the fully qualified name of the exception bubbled
	 * @param methodSig the method signature for the method that bubbled
	 */
	void bubbleException(String exception, String methodSig);

	/**
	 * Reports a marker event
	 * @param key The marker's key
	 * @param value The marker's value
	 */
	void marker(String key, String value);
}

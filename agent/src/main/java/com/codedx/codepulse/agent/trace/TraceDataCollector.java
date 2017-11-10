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

package com.codedx.codepulse.agent.trace;

/**
 * Interface for trace data collectors.
 * @author RobertF
 */
public interface TraceDataCollector
{
	/**
	 * Reports a method entry.
	 * @param methodId the ID of the method being entered
	 */
	void methodEntry(int methodId);

	/**
	 * Reports a method exit.
	 * @param methodId the ID of the method being exited
	 * @param sourceLine the line number where the method exit occurred
	 */
	void methodExit(int methodId, int sourceLine);

	/**
	 * Reports a exception.
	 * TODO: won't be called
	 * @param exception the fully qualified name of the exception thrown
	 * @param methodId the ID of method throwing the exception
	 * @param sourceLine the line number where the exception was thrown
	 */
	void exception(String exception, int methodId, int sourceLine);

	/**
	 * Reports a bubbled exception.
	 * TODO: won't have exception name
	 * @param exception the fully qualified name of the exception bubbled
	 * @param methodId the ID of the method that bubbled
	 */
	void bubbleException(String exception, int methodId);

	/**
	 * Reports a marker event
	 * @param key The marker's key
	 * @param value The marker's value
	 */
	void marker(String key, String value);
}

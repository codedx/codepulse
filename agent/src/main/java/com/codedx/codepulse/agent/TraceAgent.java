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

package com.codedx.codepulse.agent;

import com.codedx.bytefrog.instrumentation.id.*;
import com.codedx.codepulse.agent.control.Controller;
import com.codedx.codepulse.agent.control.StateManager;
import com.codedx.codepulse.agent.message.MessageSenderManager;
import com.codedx.codepulse.agent.common.config.RuntimeAgentConfigurationV1;
import com.codedx.codepulse.agent.common.config.StaticAgentConfiguration;
import com.codedx.codepulse.agent.trace.TraceDataCollector;

/**
 * Interface for Agent implementations.
 *
 * @author RobertF
 */
public interface TraceAgent
{
	/**
	 * Connect to HQ
	 *
	 * @param timeout the amount of time to try to connect to HQ for, in
	 *            seconds. A value of zero (0) will disable the timeout
	 * @return true if connection was successful before the timeout, false
	 *         otherwise
	 */
	boolean connect(int timeout) throws InterruptedException;

	/**
	 * Returns the active static configuration.
	 */
	StaticAgentConfiguration getStaticConfig();

	/**
	 * Returns the active runtime configuration.
	 */
	RuntimeAgentConfigurationV1 getConfig();

	/** Returns the active class identifier. */
	ClassIdentifier getClassIdentifier();

	/** Returns the active method identifier. */
	MethodIdentifier getMethodIdentifier();

	/**
	 * Returns the active trace data collector.
	 */
	TraceDataCollector getDataCollector();

	/**
	 * Returns the active trace state manager.
	 */
	StateManager getStateManager();

	/**
	 * Returns the active controller.
	 */
	Controller getControlController();

	/**
	 * Returns the active sender manager.
	 */
	MessageSenderManager getSenderManager();

	/**
	 * Finishes preparation/initialization before beginning tracing.
	 */
	void prepare();

	/**
	 * Starts tracing.
	 */
	void start();

	/**
	 * Immediately sends error message and halts tracing.
	 *
	 * @param errorMessage error that caused tracing to be stopped
	 */
	void killTrace(String errorMessage);

	/**
	 * Sends shutdown message.
	 */
	void shutdown();

	/**
	 * Sends shutdown message and blocks until all queued data is sent.
	 *
	 * @throws InterruptedException
	 */
	void shutdownAndWait() throws InterruptedException;

	/**
	 * Blocks until trace start command received.
	 */
	void waitForStart() throws InterruptedException;
}

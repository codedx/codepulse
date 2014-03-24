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

package com.secdec.bytefrog.agent.control;

import java.util.LinkedList;

import com.secdec.bytefrog.agent.errors.ErrorHandler;
import com.secdec.bytefrog.common.message.AgentOperationMode;

/**
 * Manages execution state and provides heartbeat information
 * @author RobertF
 */
public class StateManager
{
	private static final LinkedList<ModeChangeListener> listeners = new LinkedList<>();
	private final StateManagerControlMessageHandler messageHandler = new StateManagerControlMessageHandler();
	private AgentOperationMode currentMode = AgentOperationMode.Initializing;

	public StateManager()
	{
	}

	public void addListener(ModeChangeListener listener)
	{
		synchronized (listeners)
		{
			listeners.add(listener);
		}
	}

	public void removeListener(ModeChangeListener listener)
	{
		synchronized (listeners)
		{
			listeners.remove(listener);
		}
	}

	private void triggerModeChange(AgentOperationMode newMode)
	{
		if (currentMode != AgentOperationMode.Shutdown)
		{
			AgentOperationMode oldMode = currentMode;
			currentMode = newMode;

			synchronized (listeners)
			{
				for (ModeChangeListener listener : listeners)
					listener.onModeChange(oldMode, newMode);
			}
		}
	}

	public AgentOperationMode getCurrentMode()
	{
		return currentMode;
	}

	public ControlMessageHandler getControlMessageHandler()
	{
		return messageHandler;
	}

	public void triggerShutdown()
	{
		triggerModeChange(AgentOperationMode.Shutdown);
	}

	/**
	 * Implementation of ControlMessageHandler that invokes the appropriate
	 * changes within StateManager.
	 * @author RobertF
	 */
	private class StateManagerControlMessageHandler implements ControlMessageHandler
	{
		private boolean initSuspended = false;

		@Override
		public void onStart()
		{
			triggerModeChange(initSuspended ? AgentOperationMode.Suspended
					: AgentOperationMode.Tracing);
		}

		@Override
		public void onStop()
		{
			triggerModeChange(AgentOperationMode.Shutdown);
		}

		@Override
		public void onPause()
		{
			if (currentMode == AgentOperationMode.Tracing)
				triggerModeChange(AgentOperationMode.Paused);
			else
				ErrorHandler.handleError("pause control message is only valid when tracing");
		}

		@Override
		public void onUnpause()
		{
			if (currentMode == AgentOperationMode.Paused)
				triggerModeChange(AgentOperationMode.Tracing);
			else if (currentMode != AgentOperationMode.Shutdown)
				ErrorHandler.handleError("unpause control message is only valid when paused");
		}

		@Override
		public void onSuspend()
		{
			if (currentMode == AgentOperationMode.Initializing)
				initSuspended = true;
			else if (currentMode == AgentOperationMode.Tracing)
				triggerModeChange(AgentOperationMode.Suspended);
			else
				ErrorHandler
						.handleError("suspend control message is only valid when tracing or initializing");
		}

		@Override
		public void onUnsuspend()
		{
			if (currentMode == AgentOperationMode.Initializing)
				initSuspended = false;
			else if (currentMode == AgentOperationMode.Suspended)
				triggerModeChange(AgentOperationMode.Tracing);
			else if (currentMode != AgentOperationMode.Shutdown)
				ErrorHandler
						.handleError("unsuspend control message is only valid when suspended or initializing");
		}

		@Override
		public void onError(String error)
		{
			ErrorHandler.handleError(String.format("received error from HQ: %s", error));
		}
	}
}

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

package com.secdec.bytefrog.agent.agent;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import com.secdec.bytefrog.agent.TraceAgent;
import com.secdec.bytefrog.agent.TraceDataCollector;
import com.secdec.bytefrog.agent.control.*;
import com.secdec.bytefrog.agent.data.MessageDealerTraceDataCollector;
import com.secdec.bytefrog.agent.errors.*;
import com.secdec.bytefrog.agent.message.*;
import com.secdec.bytefrog.agent.protocol.*;
import com.secdec.bytefrog.agent.util.ShutdownHook;
import com.secdec.bytefrog.agent.util.SocketFactory;
import com.secdec.bytefrog.common.config.*;
import com.secdec.bytefrog.common.connect.SocketConnection;
import com.secdec.bytefrog.common.message.AgentOperationMode;
import com.secdec.bytefrog.common.queue.BufferPool;

/**
 * Concrete Agent implementation, manages the entire trace.
 * @author RobertF
 */
public class DefaultTraceAgent implements TraceAgent
{
	private SocketFactory socketFactory;
	private StaticAgentConfiguration staticConfig;
	private RuntimeAgentConfigurationV1 config;

	private final Semaphore startMutex = new Semaphore(0);
	private final ProtocolVersion protocol = new ProtocolVersion1();
	private LogListener logger = null;
	private TraceDataCollector dataCollector;
	private StateManager stateManager;
	private Controller controller;
	private BufferPool bufferPool;
	private BufferService bufferService;
	private MessageDealer messageFactory;
	private MessageSenderManager senderManager;
	private boolean isStarted = false;
	private boolean isKilled = false;

	public DefaultTraceAgent(StaticAgentConfiguration staticConfig)
	{
		this.staticConfig = staticConfig;
		String error = "Failed to configure logger";

		try
		{
			// add a log listener now. Wait until initialization is done to add
			// the Agent listener
			logger = new LogListener(staticConfig.getLogFilename());
			ErrorHandler.addListener(logger);

			error = "Failed to connect to HQ";
			socketFactory = new SocketFactory(staticConfig.getHqHost(), staticConfig.getHqPort());

			Socket controlSocket = socketFactory.connect();
			SocketConnection controlConnection = new SocketConnection(controlSocket, true, true);

			error = "Failed to get configuration from HQ";
			config = protocol.getControlConnectionHandshake().performHandshake(controlConnection);
			if (config == null)
			{
				controlSocket.close();
				ErrorHandler.handleError("did not receive valid configuration during handshake");
				return;
			}

			error = "Failed to initialize state manager";

			stateManager = new StateManager();

			HeartbeatInformer informer = new HeartbeatInformer()
			{
				@Override
				public int getSendQueueSize()
				{
					if (bufferPool != null)
						return bufferPool.numReadableBuffers();

					return 0;
				}

				@Override
				public AgentOperationMode getOperationMode()
				{
					return stateManager.getCurrentMode();
				}
			};

			ConfigurationHandler configHandler = new ConfigurationHandler()
			{
				@Override
				public void onConfig(RuntimeAgentConfigurationV1 newConfig)
				{
					// apply new configuration
					if (stateManager.getCurrentMode() != AgentOperationMode.Initializing)
					{
						ErrorHandler
								.handleError("reconfiguration is only valid while agent is initializing");
						return;
					}

					config = newConfig;
					controller.setHeartbeatInterval(config.getHeartbeatInterval());
				}
			};

			controller = new Controller(controlConnection, protocol, config.getHeartbeatInterval(),
					stateManager.getControlMessageHandler(), configHandler, informer);

			ModeChangeListener modeListener = new ModeChangeListener()
			{
				@Override
				public void onModeChange(AgentOperationMode oldMode, AgentOperationMode newMode)
				{
					if (oldMode == AgentOperationMode.Initializing
							&& newMode != AgentOperationMode.Shutdown)
						start();
					else if (newMode == AgentOperationMode.Shutdown)
						new Thread(new Runnable()
						{
							@Override
							public void run()
							{
								try
								{
									waitForSenderManager();
								}
								catch (InterruptedException ex)
								{
									Thread.currentThread().interrupt();
								}

								closeConnections();
							}
						}).start();
					else if (newMode == AgentOperationMode.Suspended)
						try
						{
							// alert HQ that there's a break in the data due to
							// the suspend
							controller.sendDataBreak(messageFactory.getCurrentSequence());
						}
						catch (IOException e)
						{
							ErrorHandler.handleError("Error sending data break message", e);
						}
				}
			};

			stateManager.addListener(modeListener);

			error = "Failed to start the agent controller";
			controller.start();
			error = null;

			// Now that everything is set up properly, it is safe to add an
			// AgentErrorListener.
			ErrorHandler.addListener(new AgentErrorListener(this));
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error initializing trace agent and connecting to HQ", e);
		}
		finally
		{
			if (error != null)
			{
				throw new RuntimeException("Agent Initialization Error: " + error);
				// Error initializing agent, tracing cannot run.");
			}
		}
	}

	@Override
	public void prepare()
	{
		initializeSender();
	}

	private void initializeSender()
	{
		try
		{
			// figure out the buffer count and sizes for the BufferPool
			int memBudget = config.getBufferMemoryBudget();
			int bufferLength = decideBufferLength(memBudget);
			int numBuffers = memBudget / bufferLength;

			// set up the queue/message factory
			bufferPool = new BufferPool(numBuffers, bufferLength);
			bufferService = new PooledBufferService(bufferPool, config.getQueueRetryCount());
			messageFactory = new MessageDealer(protocol.getMessageProtocol(), bufferService);
			dataCollector = new MessageDealerTraceDataCollector(messageFactory);

			senderManager = new MessageSenderManager(socketFactory,
					protocol.getDataConnectionHandshake(), bufferPool, config.getNumDataSenders(),
					config.getRunId());
			senderManager.start();

			stateManager.addListener(bufferService.getModeChangeListener());
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error initializing trace agent and connecting to HQ", e);

			throw new RuntimeException(
					"Agent Initialization Error: Failed to set up message sending system");
		}
	}

	private int decideBufferLength(int memBudget)
	{
		int len = 8192;
		int minBuffers = 10;
		while (memBudget / len < minBuffers)
		{
			if (len < 128)
			{
				throw new IllegalArgumentException(
						"Agent's memory budget is too low to accomodate enough sending buffer space");
			}
			len /= 2;
		}
		return len;
	}

	@Override
	public StaticAgentConfiguration getStaticConfig()
	{
		return staticConfig;
	}

	@Override
	public RuntimeAgentConfigurationV1 getConfig()
	{
		return config;
	}

	@Override
	public TraceDataCollector getDataCollector()
	{
		return dataCollector;
	}

	@Override
	public StateManager getStateManager()
	{
		return stateManager;
	}

	@Override
	public Controller getControlController()
	{
		return controller;
	}

	@Override
	public MessageSenderManager getSenderManager()
	{
		return senderManager;
	}

	private void registerShutdownHook()
	{
		ShutdownHook hook = new ShutdownHook()
		{
			@Override
			protected void onShutdown()
			{
				try
				{
					shutdownAndWait();
				}
				catch (InterruptedException e)
				{
					logger.onErrorReported("interrupted while waiting for final shutdown", null);
				}

				closeConnections();
				logger.close();
			}
		};

		hook.registerHook();
	}

	@Override
	public void start()
	{
		isStarted = true;
		startMutex.release();
	}

	@Override
	public void killTrace(String errorMessage)
	{
		if (!isKilled)
		{
			// send error (if we can)
			try
			{
				controller.sendError(errorMessage);
			}
			catch (IOException e)
			{
				// log directly
				logger.onErrorReported(
						String.format("IO exception sending error message: %s", e.getMessage()),
						null);
			}

			// then force a suspend and unpause
			if (bufferService != null)
			{
				// by forcing a suspend and unpause, the code being traced
				// should be able to keep running.
				bufferService.setSuspended(true);
				bufferService.setPaused(false);
			}

			shutdown();

			// if we haven't been started yet, we can exit
			if (!isStarted)
				System.exit(Integer.MIN_VALUE);

			isKilled = true;
		}
	}

	@Override
	public void shutdown()
	{
		stateManager.triggerShutdown();
	}

	@Override
	public void shutdownAndWait() throws InterruptedException
	{
		shutdown();
		waitForSenderManager();
	}

	public void closeConnections()
	{
		senderManager.shutdown();
		controller.shutdown();
	}

	private void waitForSenderManager() throws InterruptedException
	{
		int sleepInterval = config.getHeartbeatInterval();

		// poll send queue until it empties out
		do
		{
			try
			{
				Thread.sleep(sleepInterval);
			}
			catch (InterruptedException e)
			{
				ErrorHandler.handleError("interrupted while waiting for send queue to empty", e);
			}
		}
		while (!isKilled && (!senderManager.isIdle() || bufferPool.numReadableBuffers() > 0));
	}

	@Override
	public void waitForStart() throws InterruptedException
	{
		// wait for signal to start
		startMutex.acquire();

		// now that we're ready to start, register the shutdown hook
		registerShutdownHook();
	}
}

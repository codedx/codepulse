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

package com.codedx.codepulse.agent.agent;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import com.codedx.codepulse.agent.TraceAgent;
import com.codedx.codepulse.agent.control.ConfigurationHandler;
import com.codedx.codepulse.agent.control.Controller;
import com.codedx.codepulse.agent.control.HeartbeatInformer;
import com.codedx.codepulse.agent.control.ModeChangeListener;
import com.codedx.codepulse.agent.control.StateManager;
import com.codedx.codepulse.agent.data.MessageDealerTraceDataCollector;
import com.codedx.codepulse.agent.errors.AgentErrorListener;
import com.codedx.codepulse.agent.errors.ErrorHandler;
import com.codedx.codepulse.agent.errors.MinlogListener;
import com.codedx.codepulse.agent.message.BufferService;
import com.codedx.codepulse.agent.message.MessageDealer;
import com.codedx.codepulse.agent.message.MessageSenderManager;
import com.codedx.codepulse.agent.message.PooledBufferService;
import com.codedx.codepulse.agent.protocol.ProtocolVersion;
import com.codedx.codepulse.agent.protocol.ProtocolVersion3;
import com.codedx.codepulse.agent.trace.TraceDataCollector;
import com.codedx.codepulse.agent.util.ShutdownHook;
import com.codedx.codepulse.agent.util.SocketFactory;
import com.codedx.codepulse.agent.common.config.RuntimeAgentConfigurationV1;
import com.codedx.codepulse.agent.common.config.StaticAgentConfiguration;
import com.codedx.codepulse.agent.common.connect.SocketConnection;
import com.codedx.codepulse.agent.common.message.AgentOperationMode;
import com.codedx.codepulse.agent.common.queue.BufferPool;

import com.codedx.bytefrog.instrumentation.id.*;
import com.codedx.bytefrog.util.Logger;
import com.esotericsoftware.minlog.Log;

/**
 * Concrete Agent implementation, manages the entire trace.
 *
 * @author RobertF
 */
public class DefaultTraceAgent implements TraceAgent
{
	private static long ConnectSleep = 1000;

	private SocketFactory socketFactory;
	private StaticAgentConfiguration staticConfig;
	private RuntimeAgentConfigurationV1 config;

	private final Semaphore startMutex = new Semaphore(0);
	private final ProtocolVersion protocol = new ProtocolVersion3();
	private MinlogListener logger = null;
	private ClassIdentifier classIdentifier = new ClassIdentifier();
	private MethodIdentifier methodIdentifier = new MethodIdentifier();
	private TraceDataCollector dataCollector;
	private StateManager stateManager;
	private Controller controller;
	private BufferPool bufferPool;
	private BufferService bufferService;
	private MessageDealer messageFactory;
	private MessageSenderManager senderManager;
	private boolean isStarted = false;
	private boolean isKilled = false;

	public ClassIdentifier getClassIdentifier() { return classIdentifier; }
	public MethodIdentifier getMethodIdentifier() { return methodIdentifier; }

	public DefaultTraceAgent(StaticAgentConfiguration staticConfig)
	{
		this.staticConfig = staticConfig;
		String error = "Failed to configure logger";

		try
		{
			// add a log listener now. Wait until initialization is done to add
			// the Agent listener
			String logFilename = staticConfig.getLogFilename();
			File logFile = logFilename != null ? new File(logFilename) : null;
			if (logFile != null) {
				Log.setLogger(new Logger(logFile));
				logger = new MinlogListener();
				ErrorHandler.addListener(logger);
			}

			error = "Failed to initialize state manager";

			stateManager = new StateManager();

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
			error = null;
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error initializing trace agent", e);
		}
		finally
		{
			if (error != null)
			{
				throw new RuntimeException("Agent Initialization Error: " + error);
			}
		}
	}

	@Override
	public boolean connect(int timeout) throws InterruptedException
	{
		String error = "";
		long timeoutExpire = System.currentTimeMillis() + timeout * 1000;

		try
		{
			error = "Failed to connect to HQ";
			socketFactory = new SocketFactory(staticConfig.getHqHost(), staticConfig.getHqPort());

			Socket controlSocket = null;

			while (controlSocket == null
					&& (timeout == 0 || System.currentTimeMillis() <= timeoutExpire))
			{
				try
				{
					controlSocket = socketFactory.connect();
				}
				catch (IOException e)
				{
					// try again...
					controlSocket = null;
					Thread.sleep(ConnectSleep);
				}
			}

			if (controlSocket == null)
			{
				// didn't connect within timeout period
				error = null;
				return false;
			}

			SocketConnection controlConnection = new SocketConnection(controlSocket, true, true);

			error = "Failed to get configuration from HQ";
			config = protocol.getControlConnectionHandshake().performHandshake(controlConnection);
			if (config == null)
			{
				controlSocket.close();
				ErrorHandler.handleError("did not receive valid configuration during handshake");
				return false;
			}

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

			error = "Failed to start the agent controller";
			controller.start();
			error = null;

			// Now that everything is set up properly, it is safe to add an
			// AgentErrorListener.
			ErrorHandler.addListener(new AgentErrorListener(this));

			// successful connection
			return true;
		}
		catch (InterruptedException e)
		{
			// rethrow
			throw e;
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("unhandled error connecting trace agent", e);
			return false;
		}
		finally
		{
			if (error != null)
			{
				throw new RuntimeException("Agent Connection Error: " + error);
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
			messageFactory = new MessageDealer(protocol.getMessageProtocol(), bufferService, classIdentifier, methodIdentifier);
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
					if (logger != null)
					{
						logger.onErrorReported("interrupted while waiting for final shutdown", null);
					}
				}

				closeConnections();

				if (logger != null)
				{
					logger.close();
				}
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
				if (logger != null)
				{
					logger.onErrorReported(
							String.format("IO exception sending error message: %s", e.getMessage()),
							null);
				}
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

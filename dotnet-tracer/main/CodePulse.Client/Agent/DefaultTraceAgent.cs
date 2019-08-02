// Copyright 2017 Secure Decisions, a division of Applied Visions, Inc. 
// Permission is hereby granted, free of charge, to any person obtaining a copy of 
// this software and associated documentation files (the "Software"), to deal in the 
// Software without restriction, including without limitation the rights to use, copy, 
// modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, 
// and to permit persons to whom the Software is furnished to do so, subject to the 
// following conditions:
// 
// The above copyright notice and this permission notice shall be included in all copies 
// or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
// INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A 
// PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT 
// HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION 
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE 
// SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// This material is based on research sponsored by the Department of Homeland
// Security (DHS) Science and Technology Directorate, Cyber Security Division
// (DHS S&T/CSD) via contract number HHSP233201600058C.

using System;
using System.Threading;
using System.Threading.Tasks;
using CodePulse.Client.Config;
using CodePulse.Client.Connect;
using CodePulse.Client.Control;
using CodePulse.Client.Data;
using CodePulse.Client.Errors;
using CodePulse.Client.Init;
using CodePulse.Client.Instrumentation.Id;
using CodePulse.Client.Message;
using CodePulse.Client.Protocol;
using CodePulse.Client.Queue;
using CodePulse.Client.Trace;
using CodePulse.Client.Util;

namespace CodePulse.Client.Agent
{
    public class DefaultTraceAgent : ITraceAgent
    {
        private SocketFactory _socketFactory;

        private readonly IMessageProtocol _messageProtocol;
        private readonly IProtocolVersion _protocolVersion;

        private readonly StateManager _stateManager;

        private volatile bool _isKilled;
        private readonly object _isKilledLock = new object();

        private volatile bool _isShutdown;
        private readonly object _isShutdownLock = new object();

        private BufferPool _bufferPool;
        private BufferService _bufferService;
        private Controller _controller;

        private readonly HeartbeatInformer _heartbeatInformer;
        private readonly ConfigurationHandler _configurationHandler;

        private readonly ManualResetEvent _startEvent = new ManualResetEvent(false);
        private readonly ManualResetEvent _shutdownEvent = new ManualResetEvent(false);

        private readonly IErrorHandler _errorHandler = new ErrorHandler();

        private MessageSenderManager _messageSenderManager;

        public StaticAgentConfiguration StaticAgentConfiguration { get; }

        public RuntimeAgentConfiguration RuntimeAgentConfiguration { get; private set; }

        public ClassIdentifier ClassIdentifier { get; } = new ClassIdentifier();
        public MethodIdentifier MethodIdentifier { get; } = new MethodIdentifier();

        public ITraceDataCollector TraceDataCollector { get; private set; }

        public bool IsKilled
        {
            get => _isKilled;
            set => _isKilled = value;
        }

        public bool IsShutdown
        {
            get => _isShutdown;
            set => _isShutdown = value;
        }

        public DefaultTraceAgent(StaticAgentConfiguration staticAgentConfiguration)
        {
            StaticAgentConfiguration = staticAgentConfiguration ?? throw new ArgumentNullException(nameof(staticAgentConfiguration));

            _protocolVersion = new ProtocolVersion(_errorHandler);
            _messageProtocol = _protocolVersion.MessageProtocol;

            _heartbeatInformer = new HeartbeatInformer(this);
            _configurationHandler = new ConfigurationHandler(this);

            _stateManager = new StateManager(_errorHandler);
            _stateManager.AddListener(new ModeChangeListener(this));

            _errorHandler.ErrorOccurred += (sender, args) =>
            {
                var exceptionMessage = "n/a";
                if (args.Item2 != null)
                {
                    exceptionMessage = args.Item2.Message;
                }
                StaticAgentConfiguration.Logger.Error($"{args.Item1}: {exceptionMessage}", args.Item2);
            };
        }

        public bool Connect()
        {
            SocketConnection socketConnection;
            try
            {
                _socketFactory = new SocketFactory(StaticAgentConfiguration.HqHost,
                    StaticAgentConfiguration.HqPort,
                    StaticAgentConfiguration.ConnectTimeout);

                var socket = _socketFactory.Connect();
                if (socket == null)
                {
                    return false;
                }

                socketConnection = new SocketConnection(socket);
            }
            catch (Exception ex)
            {
                _errorHandler.HandleError("Failed to connect to HQ.", ex);
                return false;
            }

            try
            {
                try
                {
                    RuntimeAgentConfiguration = _protocolVersion.ControlConnectionHandshake.PerformHandshake(socketConnection, StaticAgentConfiguration.ProjectId);
                }
                catch (HandshakeException ex)
                {
                    socketConnection.Close();
                    _errorHandler.HandleError("Unable to perform control connection handshake.", ex);
                    return false;
                }
            }
            catch (Exception ex)
            {
                _errorHandler.HandleError("Failed to get configuration from HQ.", ex);
                return false;
            }

            _controller = new Controller(socketConnection,
                _protocolVersion,
                RuntimeAgentConfiguration.HeartbeatInterval,
                _stateManager.ControlMessageHandler,
                _configurationHandler,
                _heartbeatInformer,
                _errorHandler,
                StaticAgentConfiguration.Logger);

            _errorHandler.ErrorOccurred += (sender, args) => { RunKillTraceTask(args.Item1); };

            return true; 
        }

        public bool Prepare()
        {
            if (IsKilled)
            {
                return false;
            }

            try
            {
                var memBudget = RuntimeAgentConfiguration.BufferMemoryBudget;
                var bufferLength = DecideBufferLength(memBudget);
                var numBuffers = memBudget / bufferLength;

	            var logger = StaticAgentConfiguration.Logger;

				_bufferPool = new BufferPool(numBuffers, bufferLength, logger);
                _bufferService = new PooledBufferService(_bufferPool);
                TraceDataCollector = new TraceDataCollector(_messageProtocol, _bufferService, ClassIdentifier, MethodIdentifier, _errorHandler, logger);

                _messageSenderManager = new MessageSenderManager(_socketFactory,
                    _protocolVersion.DataConnectionHandshake,
                    _bufferPool,
                    RuntimeAgentConfiguration.NumDataSenders,
                    RuntimeAgentConfiguration.RunId,
                    _errorHandler,
                    logger);

                return true;
            }
            catch (Exception e)
            {
                _errorHandler.HandleError("Error initializing trace agent and connecting to HQ.", e);
                return false;
            }
        }

        public void Start()
        {
            _startEvent.Set();
        }

        public void WaitForStart()
        {
            _startEvent.WaitOne();
        }

        public void Shutdown()
        {
            _stateManager.TriggerShutdown();
        }

        public void WaitForShutdown()
        {
            _shutdownEvent.WaitOne();
        }

        private void KillTrace(string errorMessage)
        {
            var lockAcquired = Monitor.TryEnter(_isKilledLock);
            if (!lockAcquired)
            {
                return;
            }

            try
            {
                if (IsKilled)
                {
                    return;
                }

                try
                {
                    _controller.SendError(errorMessage);
                }
                catch (Exception ex)
                {
                    _errorHandler.HandleError("Exception occurred while sending error message.", ex);
                }

                Shutdown();

                // release any callers awaiting a start that will not occur
                _startEvent.Set();

                IsKilled = true;
            }
            finally
            {
                Monitor.Exit(_isKilledLock);
            }
        }

        private void ShutdownAgent()
        {
            var lockAcquired = Monitor.TryEnter(_isShutdownLock);
            if (!lockAcquired)
            {
                return;
            }

            try
            {
                if (IsShutdown)
                {
                    return;
                }

                // the TraceDataCollector shutdown will use the buffer service to send any pending 
                // method-entry messages - if the service is suspended, the messages will not reach Code Pulse
                TraceDataCollector?.Shutdown();

                if (_bufferService != null)
                { 
                    _bufferService.SetSuspended(true);
                    if (_bufferService.IsPaused)
                    {
                        _bufferService.SetPaused(false);
                    }
                }

                WaitForSenderManager();
                _messageSenderManager?.Shutdown();
                _controller?.Shutdown();

                IsShutdown = true;

                _shutdownEvent.Set();
            }
            finally
            {
                Monitor.Exit(_isShutdownLock);
            }
        }

        private void RunShutdownAgentTask()
        {
            Task.Run(() =>
            {
                ShutdownAgent();
            });
        }

        private void RunKillTraceTask(string errorMessage)
        {
            Task.Run(() =>
            {
                KillTrace(errorMessage);
            });
        }

        private void WaitForSenderManager()
        {
            if (_messageSenderManager == null)
            {
                return;
            }

            var sleepInterval = RuntimeAgentConfiguration.HeartbeatInterval;
            do
            {
                Thread.Sleep(sleepInterval);
            }
            while (!IsKilled && (!_messageSenderManager.IsIdle || _bufferPool.ReadableBuffers > 0));
        }

        private int DecideBufferLength(int memBudget)
        {
            var len = 8192;
            var minBuffers = 10;
            while (memBudget / len < minBuffers)
            {
                if (len < 128)
                {
                    throw new ArgumentException("Agent's memory budget is too low to accomodate enough sending buffer space");
                }
                len /= 2;
            }
            return len;
        }

        private class HeartbeatInformer : IHeartbeatInformer
        {
            private readonly DefaultTraceAgent _agent;

            public AgentOperationMode OperationMode => _agent._stateManager.CurrentMode;

            public int SendQueueSize => _agent._bufferPool?.ReadableBuffers ?? 0;

            public HeartbeatInformer(DefaultTraceAgent agent)
            {
                _agent = agent;
            }
        }

        private class ConfigurationHandler : IConfigurationHandler
        {
            private readonly DefaultTraceAgent _agent;
            
            public ConfigurationHandler(DefaultTraceAgent agent)
            {
                _agent = agent;
            }

            public void OnConfig(RuntimeAgentConfiguration config)
            {
                if (_agent._stateManager.CurrentMode != AgentOperationMode.Initializing)
                {
                    throw new InvalidOperationException("Reconfiguration is only valid while agent is running");
                }

                _agent.RuntimeAgentConfiguration = config;
                _agent._controller.SetHeartbeatInterval(config.HeartbeatInterval);
            }
        }

        private class ModeChangeListener : IModeChangeListener
        {
            private readonly DefaultTraceAgent _agent;

            public ModeChangeListener(DefaultTraceAgent agent)
            {
                _agent = agent;
            }

            public void OnModeChange(AgentOperationMode oldMode, AgentOperationMode newMode)
            {
                if (oldMode == AgentOperationMode.Initializing && newMode != AgentOperationMode.Shutdown)
                {
                    _agent.Start();
                    return;
                }

                switch (newMode)
                {
                    case AgentOperationMode.Initializing:
                        break;

                    case AgentOperationMode.Tracing:
                        switch (oldMode)
                        {
                            case AgentOperationMode.Paused:
                                _agent._bufferService?.SetPaused(false);
                                break;
                            case AgentOperationMode.Suspended:
                                _agent._bufferService?.SetSuspended(false);
                                break;
                        }
                        break;

                    case AgentOperationMode.Paused:
                        _agent._bufferService?.SetPaused(true);
                        break;

                    case AgentOperationMode.Suspended:
                        try
                        {
                            _agent._controller.SendDataBreak(_agent.TraceDataCollector.SequenceId);
                        }
                        catch (Exception ex)
                        {
                            _agent._errorHandler.HandleError("Error sending data break message.", ex);
                        }
                        _agent._bufferService?.SetSuspended(true);
                        break;

                    case AgentOperationMode.Shutdown:
                        _agent.RunShutdownAgentTask();
                        break;

                    default:
                        throw new ArgumentOutOfRangeException(nameof(newMode), newMode, null);
                }
            }
        }
    }
}


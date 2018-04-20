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
using System.IO;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using CodePulse.Client.Connect;
using CodePulse.Client.Errors;
using CodePulse.Client.Protocol;
using CodePulse.Client.Util;
using log4net;

namespace CodePulse.Client.Control
{
    public class Controller
    {
        private readonly SocketConnection _socketConnection;
        private readonly IProtocolVersion _protocolVersion;
        private int _heartbeatInterval;
        private readonly ILog _logger;
        private readonly IErrorHandler _errorHandler;
        private readonly IControlMessageProcessor _messageProcessor;
        private readonly IHeartbeatInformer _heartbeatInformer;

        private readonly BinaryReader _inputReader;
        private readonly BinaryWriter _outputWriter;

        private readonly CancellationTokenSource _cancellationTokenSource = new CancellationTokenSource();

        private readonly object _sendDataObj = new object();

        private readonly Task _task;

        public bool IsRunning => _task.Status == TaskStatus.Running;

        public Controller(SocketConnection socketConnection,
            IProtocolVersion protocolVersion,
            int heartbeatInterval,
            IControlMessageHandler messageHandler,
            IConfigurationHandler configurationHandler,
            IHeartbeatInformer heartbeatInformer,
            IErrorHandler errorHandler,
            ILog logger)
        {
            if (messageHandler == null) throw new ArgumentNullException(nameof(messageHandler));
            if (heartbeatInterval <= 0) throw new ArgumentOutOfRangeException(nameof(heartbeatInterval));

            _socketConnection = socketConnection ?? throw new ArgumentNullException(nameof(socketConnection));
            _protocolVersion = protocolVersion ?? throw new ArgumentNullException(nameof(protocolVersion));
            _heartbeatInterval = heartbeatInterval;
            _errorHandler = errorHandler ?? throw new ArgumentNullException(nameof(errorHandler));
            _logger = logger ?? throw new ArgumentNullException(nameof(logger));
            _heartbeatInformer = heartbeatInformer ?? throw new ArgumentNullException(nameof(heartbeatInformer));
            _messageProcessor = _protocolVersion.GetControlMessageProcessor(messageHandler, configurationHandler);

            _inputReader = _socketConnection.InputReader;
            _outputWriter = _socketConnection.OutputWriter;

            _task = Task.Run(() => RunController());
        }

        public void SetHeartbeatInterval(int heartbeatInterval)
        {
            if (heartbeatInterval <= 0) throw new ArgumentOutOfRangeException(nameof(heartbeatInterval));
            _heartbeatInterval = heartbeatInterval;
        }

        public void Shutdown()
        {
            if (_task == null)
            {
                return;
            }

            try
            {
                _cancellationTokenSource.Cancel();
                _task.Wait();
            }
            catch (AggregateException aex)
            {
                aex.Handle(ex =>
                {
                    if (!(ex is TaskCanceledException))
                    {
                        return false;
                    }

                    _errorHandler.HandleError("Exception occurred when stopping agent controller.", ex);
                    return true;
                });
            }
            finally
            {
                _socketConnection.Close();
            }
        }

        public void SendError(string errorMessage)
        {
            lock (_sendDataObj)
            {
                _protocolVersion.MessageProtocol.WriteError(_outputWriter, errorMessage);
                _outputWriter.FlushAndLog("SendError");
            }
        }

        public void SendDataBreak(int sequence)
        {
            lock (_sendDataObj)
            {
                _protocolVersion.MessageProtocol.WriteDataBreak(_outputWriter, sequence);
                _outputWriter.FlushAndLog("SendDataBreak");
            }
        }

        private void RunController()
        {
            try
            {
                var nextHeartbeat = DateTime.UtcNow;
                while (!_cancellationTokenSource.IsCancellationRequested)
                {
                    // drain incoming messages before attempting to write
                    do
                    {
                    }
                    while (ProcessIncomingMessage(100, _cancellationTokenSource.Token, true));

                    if (DateTime.UtcNow > nextHeartbeat)
                    {
                        SendHeartbeat();
                        nextHeartbeat = DateTime.UtcNow.AddMilliseconds(_heartbeatInterval);
                    }

                    var timeout = Math.Max(nextHeartbeat.Subtract(DateTime.UtcNow).TotalMilliseconds, 1);
                    ProcessIncomingMessage((int)timeout, _cancellationTokenSource.Token, false);
                }

                _logger.Info("Controller received token cancellation request.");
            }
            catch (Exception ex)
            {
                _errorHandler.HandleError("Controller failed and will no longer process messages.", ex);
            }
        }

        private void SendHeartbeat()
        {
            var mode = _heartbeatInformer.OperationMode;
            var sendQueueSize = (ushort)Math.Min(_heartbeatInformer.SendQueueSize, UInt16.MaxValue);

            lock (_sendDataObj)
            {
                _protocolVersion.MessageProtocol.WriteHeartbeat(_outputWriter, mode, sendQueueSize);
                _outputWriter.FlushAndLog("SendHeartbeat");
            }
        }

        private bool ProcessIncomingMessage(int timeout, CancellationToken token, bool availableDataOnly)
        {
            if (timeout <= 0)
            {
                throw new ArgumentOutOfRangeException(nameof(timeout));
            }

			if (availableDataOnly && !_socketConnection.IsDataAvailable)
	        {
		        return false;
	        }

            _socketConnection.SetReceiveTimeout(timeout);
            try
            {
                _messageProcessor.ProcessIncomingMessage(_inputReader);
                return true;
            }
            catch (IOException e)
            {
                var socketException = e.InnerException as SocketException;
                if (socketException?.SocketErrorCode == SocketError.TimedOut || token.IsCancellationRequested)
                {
                    return false;
                }
                throw;
            }
        }
    }
}

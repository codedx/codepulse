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
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using CodePulse.Client.Connect;
using CodePulse.Client.Errors;
using CodePulse.Client.Init;
using CodePulse.Client.Queue;
using CodePulse.Client.Util;
using log4net;

namespace CodePulse.Client.Message
{
    public class MessageSenderManager
    {
        private readonly SocketFactory _socketFactory;
        private readonly IDataConnectionHandshake _dataConnectionHandshake;
        private readonly BufferPool _bufferPool;
        private readonly byte _runId;
        private readonly ILog _logger;
        private readonly IErrorHandler _errorHandler;

        private readonly int _numSenders;
        private readonly IList<IConnection> _connections = new List<IConnection>();
        private readonly IList<PooledMessageSender> _senders = new List<PooledMessageSender>();

        public int CurrentSenderCount => _senders.Count;

        public int CurrentConnectionCount => _connections.Count;

        public bool IsShutdown
        {
            get
            {
                return _senders.All(x => x.IsShutdown);
            }
        }

        public bool IsIdle
        {
            get
            {
                return IsShutdown || _senders.All(x => x.IsIdle);
            }
        }

        public MessageSenderManager(SocketFactory socketFactory,
            IDataConnectionHandshake dataConnectionHandshake,
            BufferPool bufferPool,
            int numSenders,
            byte runId,
            IErrorHandler errorHandler,
            ILog logger)
        {
            _bufferPool = bufferPool ?? throw new ArgumentNullException(nameof(bufferPool));
            _dataConnectionHandshake = dataConnectionHandshake ?? throw new ArgumentNullException(nameof(dataConnectionHandshake));
            _socketFactory = socketFactory ?? throw new ArgumentNullException(nameof(socketFactory));
            if (numSenders <= 0) throw new ArgumentOutOfRangeException(nameof(numSenders));
            _numSenders = numSenders;
            _runId = runId;
            _errorHandler = errorHandler ?? throw new ArgumentNullException(nameof(errorHandler));
            _logger = logger ?? throw new ArgumentNullException(nameof(logger));

            if (!Start())
            {
                Shutdown();
            }
        }

        public void Shutdown()
        {
            ShutdownSenders();
            CloseConnections();
        }

        private bool Start()
        {
            try
            {
                for (var i = 0; i < _numSenders; i++)
                {
                    var socket = _socketFactory.Connect();
                    if (socket == null)
                    {
                        _errorHandler.HandleError($"Failed to open HQ Data connection to host {_socketFactory.Host} on port {_socketFactory.Port} with a retry of {_socketFactory.RetryDurationInMilliseconds} millisecond(s).");
                        return false;
                    }

                    var socketConnection = new SocketConnection(socket);
                    try
                    {
                        _dataConnectionHandshake.PerformHandshake(_runId, socketConnection);
                    }
                    catch (HandshakeException ex)
                    {
                        socketConnection.Close();

                        _errorHandler.HandleError("Unable to perform data connection handshake.", ex);
                        return false;
                    }

                    _connections.Add(socketConnection);
                    _senders.Add(new PooledMessageSender(_bufferPool,
                        socketConnection.OutputWriter,
                        _errorHandler,
                        _logger));
                }
            }
            catch (Exception ex)
            {
                _errorHandler.HandleError("Failed to start the MessageSenderManager.", ex);
                return false;
            }

            return true;
        }

        private void ShutdownSenders()
        {
            foreach (var sender in _senders)
            {
                sender.Shutdown();
            }
            while (_senders.Any(x => !x.IsShutdown))
            {
                Thread.Sleep(50);
            }
            _senders.Clear();
        }

        private void CloseConnections()
        {
            foreach (var connection in _connections)
            {
                connection.Close();
            }
            _connections.Clear();
        }
    }
}

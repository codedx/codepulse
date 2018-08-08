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
using System.Net.Sockets;
using System.Threading;
using CodePulse.Client.Config;
using CodePulse.Client.Connect;
using CodePulse.Client.Control;
using CodePulse.Client.Errors;
using CodePulse.Client.Message;
using CodePulse.Client.Protocol;
using CodePulse.Client.Util;
using log4net;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Moq;

namespace CodePulse.Client.Test
{
    class ConfigurationHandler : IConfigurationHandler
    {
        private readonly ManualResetEventSlim _onConfigHappened;

        public ConfigurationHandler(ManualResetEventSlim onConfigHappened)
        {
            _onConfigHappened = onConfigHappened;
        }
        public void OnConfig(RuntimeAgentConfiguration config)
        {
            _onConfigHappened.Set();
        }
    }

    class ControlMessageHandler : IControlMessageHandler
    {
        private readonly ManualResetEventSlim _onStartHappened;

        public ControlMessageHandler(ManualResetEventSlim onStartHappened)
        {
            _onStartHappened = onStartHappened;
        }

        public void OnStart()
        {
            _onStartHappened.Set();
        }

        public void OnStop()
        {
            throw new NotImplementedException();
        }

        public void OnPause()
        {
            throw new NotImplementedException();
        }

        public void OnUnpause()
        {
            throw new NotImplementedException();
        }

        public void OnSuspend()
        {
            throw new NotImplementedException();
        }

        public void OnUnsuspend()
        {
            throw new NotImplementedException();
        }

        public void OnError(string error)
        {
            throw new NotImplementedException();
        }
    }

    class HeartbeatInformer : IHeartbeatInformer
    {
        public AgentOperationMode OperationMode { get; set; }
        public int SendQueueSize { get; set; }
    }

    [TestClass]
    public class ControllerTests
    {
        [TestMethod]
        public void WhenControllerStartsItProcessesMessages()
        {
            // arrange
            var okayToWriteEvent = new ManualResetEventSlim();
            var closeSocketEvent = new ManualResetEventSlim();
            var onStartHappened = new ManualResetEventSlim();

            var listeningEvent = new ManualResetEventSlim();
            var serverTask = Server.CreateServer(4998, listeningEvent, 
                listener =>
                {
                    var socket = listener.Accept();

                    okayToWriteEvent.Wait();
                    socket.Send(new[] { MessageTypes.Start });
                    closeSocketEvent.Wait();

                    socket.Shutdown(SocketShutdown.Both);
                    socket.Close();
                });

            if (!listeningEvent.Wait(5000))
            {
                Assert.Fail("Expected server to start listening");
            }

            var socketFactory = new SocketFactory("127.0.0.1", 4998, 1);
            var errorHandler = new ErrorHandler();

            // act
            var controller = new Controller(
                new SocketConnection(socketFactory.Connect()),
                new ProtocolVersion(errorHandler),
                10,
                new ControlMessageHandler(onStartHappened),
                new Mock<IConfigurationHandler>().Object,
                new HeartbeatInformer(),
                errorHandler,
                new Mock<ILog>().Object);

            okayToWriteEvent.Set();
            onStartHappened.Wait();

            // assert
            Assert.IsTrue(controller.IsRunning);

            controller.Shutdown();

            closeSocketEvent.Set();
            serverTask.Wait(TimeSpan.FromMilliseconds(5000));
            serverTask.Dispose();
        }

        [TestMethod]
        public void WhenControllerHeartbeatIntervalChangesHeartbeatChanges()
        {
            // arrange
            var closeSocketEvent = new ManualResetEventSlim();
            var listeningEvent = new ManualResetEventSlim();

            var heartbeatTimes = new List<DateTime>();
            var serverTask = Server.CreateServer(4998, listeningEvent, 
                listener =>
                {
                    var socket = listener.Accept();
                    socket.ReceiveTimeout = 1;

                    while (!closeSocketEvent.Wait(TimeSpan.FromMilliseconds(1)))
                    {
                        var buffer = new byte[50];
                        try
                        {
                            var bytesRead = socket.Receive(buffer);
                            if (bytesRead > 0)
                            {
                                if (buffer[0] == MessageTypes.Heartbeat)
                                {
                                    heartbeatTimes.Add(DateTime.UtcNow);
                                }
                                Array.Clear(buffer, 0, buffer.Length);
                            }
                        }
                        catch
                        {
                            // ignored
                        }
                    }

                    closeSocketEvent.Wait();
                    socket.Shutdown(SocketShutdown.Both);
                    socket.Close();
                });

            if (!listeningEvent.Wait(5000))
            {
                Assert.Fail("Expected server to start listening");
            }

            var socketFactory = new SocketFactory("127.0.0.1", 4998, 1);
            var errorHandler = new ErrorHandler();

            // act
            var controller = new Controller(
                new SocketConnection(socketFactory.Connect()),
                new ProtocolVersion(errorHandler),
                3000,
                new Mock<IControlMessageHandler>().Object,
                new Mock<IConfigurationHandler>().Object,
                new HeartbeatInformer(),
                errorHandler,
                new Mock<ILog>().Object);

            Thread.Sleep(10000);
            controller.SetHeartbeatInterval(500);
            Thread.Sleep(10000);

            controller.Shutdown();

            // assert
            Assert.IsTrue(heartbeatTimes.Count > 4);

            var slowHeartbeatTimeFrequency = heartbeatTimes[2].Subtract(heartbeatTimes[1]).TotalMilliseconds;
            var fastHeartbeatTimeFrequency = heartbeatTimes[heartbeatTimes.Count-1].Subtract(heartbeatTimes[heartbeatTimes.Count-2]).TotalMilliseconds;

            // Runs in a local environment have heartbeats at ~3100 for slow and ~600 for fast, but on the build 
            // server heartbeats can be at ~4100 for slow and ~1600 for fast, so pass this test if there's a 
            // significant difference between the slow and fast heartbeats
            if (slowHeartbeatTimeFrequency - fastHeartbeatTimeFrequency <= 1750)
            {
                for (var index = 0; index < heartbeatTimes.Count; index++)
                {
                    if (index == 0)
                        continue;

                    Console.WriteLine(heartbeatTimes[index].Subtract(heartbeatTimes[index - 1]).TotalMilliseconds);
                }
                Assert.Fail($"Unexpected values for fastHeartbeatTimeFrequency ({fastHeartbeatTimeFrequency}) and slowHeartbeatTimeFrequency ({slowHeartbeatTimeFrequency})");
            }

            closeSocketEvent.Set();
            serverTask.Wait();
            serverTask.Dispose();
        }
    }
}

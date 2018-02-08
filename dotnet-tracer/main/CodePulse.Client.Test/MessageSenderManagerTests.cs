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

using System.Net.Sockets;
using System.Threading;
using CodePulse.Client.Errors;
using CodePulse.Client.Init;
using CodePulse.Client.Message;
using CodePulse.Client.Queue;
using CodePulse.Client.Util;
using log4net;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Moq;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class MessageSenderManagerTests
    {
        [TestMethod]
        public void WhenStartedWithOneSenderManagerIsIdle()
        {
            var listeningEvent = new ManualResetEventSlim();
            var closeSocketEvent = new ManualResetEventSlim();

            var serverTask = Server.CreateServer(4998, listeningEvent,
                listener =>
                {
                    var socket = listener.Accept();

                    // receive data hello and reply
                    var buffer = new byte[256];
                    var bytesReceived = socket.Receive(buffer);
                    if (bytesReceived == 2 && buffer[0] == MessageTypes.DataHello)
                    {
                        socket.Send(new[] { MessageTypes.DataHelloReply });
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

            // act
            var messageSenderManager = new MessageSenderManager(socketFactory,
                new DataConnectionHandshake(new MessageProtocol()),
                new BufferPool(1, 9),
                1,
                1,
                new ErrorHandler(),
                new Mock<ILog>().Object);

            // assert
            Assert.IsTrue(messageSenderManager.IsIdle);

            Assert.AreEqual(1, messageSenderManager.CurrentConnectionCount);
            Assert.AreEqual(1, messageSenderManager.CurrentSenderCount);

            messageSenderManager.Shutdown();
            Assert.IsTrue(messageSenderManager.IsShutdown);

            closeSocketEvent.Set();
            serverTask.Wait();
            serverTask.Dispose();
        }

        [TestMethod]
        public void WhenStartedWithTwoSenderManagersIsIdle()
        {
            var listeningEvent = new ManualResetEventSlim();
            var closeSocket1Event = new ManualResetEventSlim();
            var closeSocket2Event = new ManualResetEventSlim();

            var serverTask = Server.CreateServer(4998, listeningEvent,
                listener =>
                {
                    var socket = listener.Accept();

                    // receive data hello and reply
                    var buffer = new byte[256];
                    var bytesReceived = socket.Receive(buffer);
                    if (bytesReceived == 2 && buffer[0] == MessageTypes.DataHello)
                    {
                        socket.Send(new[] { MessageTypes.DataHelloReply });
                    }

                    closeSocket1Event.Wait();

                    socket.Shutdown(SocketShutdown.Both);
                    socket.Close();
                },
                listener =>
                {
                    var socket = listener.Accept();

                    // receive data hello and reply
                    var buffer = new byte[256];
                    var bytesReceived = socket.Receive(buffer);
                    if (bytesReceived == 2 && buffer[0] == MessageTypes.DataHello)
                    {
                        socket.Send(new[] { MessageTypes.DataHelloReply });
                    }

                    closeSocket2Event.Wait();

                    socket.Shutdown(SocketShutdown.Both);
                    socket.Close();
                });

            if (!listeningEvent.Wait(5000))
            {
                Assert.Fail("Expected server to start listening");
            }

            var socketFactory = new SocketFactory("127.0.0.1", 4998, 1);

            // act
            var messageSenderManager = new MessageSenderManager(socketFactory,
                new DataConnectionHandshake(new MessageProtocol()),
                new BufferPool(1, 9),
                2,
                1,
                new ErrorHandler(),
                new Mock<ILog>().Object);

            // assert
            Assert.IsTrue(messageSenderManager.IsIdle);

            Assert.AreEqual(2, messageSenderManager.CurrentConnectionCount);
            Assert.AreEqual(2, messageSenderManager.CurrentSenderCount);

            messageSenderManager.Shutdown();
            Assert.IsTrue(messageSenderManager.IsShutdown);

            closeSocket1Event.Set();
            closeSocket2Event.Set();
            serverTask.Wait();
            serverTask.Dispose();
        }

        [TestMethod]
        public void WhenNoConnectionErrorOccursWithShutdown()
        {
            var socketFactory = new SocketFactory("127.0.0.1", 4998, 1);

            string errorMessage = null;
            string exceptionMessage = null;
            var errorHandler = new ErrorHandler();
            errorHandler.ErrorOccurred += (sender, tuple) => { errorMessage = tuple.Item1; exceptionMessage = tuple.Item2?.Message; };

            // act
            var messageSenderManager = new MessageSenderManager(socketFactory,
                new DataConnectionHandshake(new MessageProtocol()),
                new BufferPool(1, 9),
                1,
                1,
                errorHandler,
                new Mock<ILog>().Object);

            // assert
            Assert.IsTrue(messageSenderManager.IsShutdown);
            Assert.AreEqual("Failed to open HQ Data connection to host 127.0.0.1 on port 4998 with a retry of 1 millisecond(s).", errorMessage);
            Assert.IsNull(exceptionMessage);
        }

        [TestMethod]
        public void WhenNoConnectionForOneOfTwoErrorOccursWithShutdown()
        {
            var listeningEvent = new ManualResetEventSlim();
            var closeSocket1Event = new ManualResetEventSlim();
            var closeSocket2Event = new ManualResetEventSlim();

            var replyType = 0;
            var serverTask = Server.CreateServer(4998, listeningEvent,
                listener =>
                {
                    var socket = listener.Accept();

                    Interlocked.Increment(ref replyType);

                    // receive data hello and reply
                    var buffer = new byte[256];
                    var bytesReceived = socket.Receive(buffer);
                    if (bytesReceived == 2 && buffer[0] == MessageTypes.DataHello)
                    {
                        socket.Send(new[] { replyType == 1 ? MessageTypes.DataHelloReply : (byte)0xFE });
                    }

                    // wait for socket close
                    closeSocket1Event.Wait();

                    socket.Shutdown(SocketShutdown.Both);
                    socket.Close();
                }, 
                listener =>
                {
                    var socket = listener.Accept();

                    Interlocked.Increment(ref replyType);

                    // receive data hello and reply
                    var buffer = new byte[256];
                    var bytesReceived = socket.Receive(buffer);
                    if (bytesReceived == 2 && buffer[0] == MessageTypes.DataHello)
                    {
                        socket.Send(new[] { replyType == 1 ? MessageTypes.DataHelloReply : (byte)0xFE });
                    }

                    // wait for socket close
                    closeSocket2Event.Wait();

                    socket.Shutdown(SocketShutdown.Both);
                    socket.Close();
                });

            if (!listeningEvent.Wait(5000))
            {
                Assert.Fail("Expected server to start listening");
            }

            var socketFactory = new SocketFactory("127.0.0.1", 4998, 1);

            string errorMessage = null;
            string exceptionMessage = null;
            var errorHandler = new ErrorHandler();
            errorHandler.ErrorOccurred += (sender, tuple) => { errorMessage = tuple.Item1; exceptionMessage = tuple.Item2?.Message; };

            // act
            var messageSenderManager = new MessageSenderManager(socketFactory,
                new DataConnectionHandshake(new MessageProtocol()),
                new BufferPool(1, 9),
                2,
                1,
                errorHandler,
                new Mock<ILog>().Object);

            // assert
            Assert.AreEqual(0, messageSenderManager.CurrentConnectionCount);
            Assert.AreEqual(0, messageSenderManager.CurrentSenderCount);
            Assert.IsTrue(messageSenderManager.IsShutdown);
            Assert.AreEqual("Unable to perform data connection handshake.", errorMessage);
            Assert.AreEqual("Handshake operation failed with unexpected reply: 254", exceptionMessage);

            closeSocket1Event.Set();
            closeSocket2Event.Set();
            serverTask.Wait();
            serverTask.Dispose();
        }
    }
}

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

using System.IO;
using CodePulse.Client.Config;
using CodePulse.Client.Control;
using CodePulse.Client.Errors;
using CodePulse.Client.Message;
using CodePulse.Client.Util;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Moq;
using Newtonsoft.Json;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class ControlMessageProcessorTests
    {
        [TestMethod]
        public void WhenStartReceivedMessageHandlerStartInvoked()
        {
            // arrange
            var onMethodCalled = false;
            var messageHandler = new Mock<IControlMessageHandler>();
            messageHandler.Setup(x => x.OnStart()).Callback(() => onMethodCalled = true);

            // act
            ProcessControlMessage(MessageTypes.Start, messageHandler);

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        [TestMethod]
        public void WhenStopReceivedMessageHandlerStopInvoked()
        {
            // arrange
            var onMethodCalled = false;
            var messageHandler = new Mock<IControlMessageHandler>();
            messageHandler.Setup(x => x.OnStop()).Callback(() => onMethodCalled = true);

            // act
            ProcessControlMessage(MessageTypes.Stop, messageHandler);

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        [TestMethod]
        public void WhenPauseReceivedMessageHandlerPauseInvoked()
        {
            // arrange
            var onMethodCalled = false;
            var messageHandler = new Mock<IControlMessageHandler>();
            messageHandler.Setup(x => x.OnPause()).Callback(() => onMethodCalled = true);

            // act
            ProcessControlMessage(MessageTypes.Pause, messageHandler);

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        [TestMethod]
        public void WhenUnpauseReceivedMessageHandlerUnpauseInvoked()
        {
            // arrange
            var onMethodCalled = false;
            var messageHandler = new Mock<IControlMessageHandler>();
            messageHandler.Setup(x => x.OnUnpause()).Callback(() => onMethodCalled = true);

            // act
            ProcessControlMessage(MessageTypes.Unpause, messageHandler);

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        [TestMethod]
        public void WhenSuspendReceivedMessageHandlerSuspendInvoked()
        {
            // arrange
            var onMethodCalled = false;
            var messageHandler = new Mock<IControlMessageHandler>();
            messageHandler.Setup(x => x.OnSuspend()).Callback(() => onMethodCalled = true);

            // act
            ProcessControlMessage(MessageTypes.Suspend, messageHandler);

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        [TestMethod]
        public void WhenUnsuspendReceivedMessageHandlerUnsuspendInvoked()
        {
            // arrange
            var onMethodCalled = false;
            var messageHandler = new Mock<IControlMessageHandler>();
            messageHandler.Setup(x => x.OnUnsuspend()).Callback(() => onMethodCalled = true);

            // act
            ProcessControlMessage(MessageTypes.Unsuspend, messageHandler);

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        [TestMethod]
        public void WhenErrorReceivedMessageHandleErrorInvoked()
        {
            // arrange
            var onMethodCalled = false;
            var messageHandler = new Mock<IControlMessageHandler>();
            messageHandler.Setup(x => x.OnError("Error")).Callback(() => onMethodCalled = true);

            using (var buffer = new MemoryStream())
            using (var writer = new BinaryWriter(buffer))
            { 
                writer.Write(MessageTypes.Error);
                writer.WriteUtfBigEndian("Error");

                // act
                ProcessControlMessage(buffer.ToArray(), messageHandler);
            }

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        [TestMethod]
        public void WhenConfigurationReceivedMessageHandleConfigInvoked()
        {
            // arrange
            var onMethodCalled = false;

            var messageHandler = new Mock<IControlMessageHandler>();
            var configurationHandler = new Mock<IConfigurationHandler>();
            configurationHandler.Setup(x => x.OnConfig(It.IsAny<RuntimeAgentConfiguration>())).Callback(() => onMethodCalled = true);

            using (var buffer = new MemoryStream())
            using (var writer = new BinaryWriter(buffer))
            {
                writer.Write(MessageTypes.Configuration);

                var runtimeAgentConfiguration = new RuntimeAgentConfiguration(1, 1, new string[0], new string[0], 1, 1);
                var runtimeAgentConfigurationJson = JsonConvert.SerializeObject(runtimeAgentConfiguration);
                writer.WriteUtfBigEndian(runtimeAgentConfigurationJson);

                // act
                ProcessControlMessage(buffer.ToArray(), messageHandler, configurationHandler);
            }

            // assert
            Assert.IsTrue(onMethodCalled);
        }

        private static void ProcessControlMessage(byte message, IMock<IControlMessageHandler> messageHandler)
        {
            var messages = new[] { message };
            ProcessControlMessage(messages, messageHandler);
        }

        private static void ProcessControlMessage(byte[] messages, IMock<IControlMessageHandler> messageHandler)
        {
            ProcessControlMessage(messages, messageHandler, new Mock<IConfigurationHandler>());
        }

        private static void ProcessControlMessage(byte[] messages, IMock<IControlMessageHandler> messageHandler, IMock<IConfigurationHandler> configurationHandler)
        {
            using (var messagesStream = new MemoryStream(messages))
            using (var binaryReader = new BinaryReader(messagesStream))
            {
                var processor = new ControlMessageProcessor(new ConfigurationReader(),
                    messageHandler.Object,
                    configurationHandler.Object,
                    new ErrorHandler());

                processor.ProcessIncomingMessage(binaryReader);
            }
        }
    }
}

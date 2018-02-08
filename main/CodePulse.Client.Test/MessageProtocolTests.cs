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
using CodePulse.Client.Message;
using CodePulse.Client.Util;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class MessageProtocolTests
    {
        [TestMethod]
        public void WhenWriteHelloMessageCreated()
        {
            TestMessage((writer, protocol) => protocol.WriteHello(writer),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.Hello, reader.ReadByte());
                    Assert.AreEqual(protocol.ProtocolVersion, reader.ReadByte());
                });
        }

        [TestMethod]
        public void WhenWriteDataHelloMessageCreated()
        {
            const byte runId = 1;
            TestMessage((writer, protocol) => protocol.WriteDataHello(writer, runId),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.DataHello, reader.ReadByte());
                    Assert.AreEqual(runId, reader.ReadByte());
                });
        }

        [TestMethod]
        public void WhenWriteErrorMessageCreated()
        {
            const string errorMessage = "Error";
            TestMessage((writer, protocol) => protocol.WriteError(writer, errorMessage),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.Error, reader.ReadByte());
                    Assert.AreEqual(errorMessage, reader.ReadUtfBigEndian());
                });
        }

        [TestMethod]
        public void WhenInitializingHeartbeatMessageCreated()
        {
            const byte messageData = 73;
            TestMessage((writer, protocol) => protocol.WriteHeartbeat(writer, AgentOperationMode.Initializing, ushort.MaxValue),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.Heartbeat, reader.ReadByte());
                    Assert.AreEqual(messageData, reader.ReadByte());
                    Assert.AreEqual(ushort.MaxValue, reader.ReadUInt16BigEndian());
                });
        }

        [TestMethod]
        public void WhenPausedHeartbeatMessageCreated()
        {
            const byte messageData = 80;
            TestMessage((writer, protocol) => protocol.WriteHeartbeat(writer, AgentOperationMode.Paused, ushort.MaxValue),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.Heartbeat, reader.ReadByte());
                    Assert.AreEqual(messageData, reader.ReadByte());
                    Assert.AreEqual(ushort.MaxValue, reader.ReadUInt16BigEndian());
                });
        }

        [TestMethod]
        public void WhenSuspendedHeartbeatMessageCreated()
        {
            const byte messageData = 83;
            TestMessage((writer, protocol) => protocol.WriteHeartbeat(writer, AgentOperationMode.Suspended, ushort.MaxValue),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.Heartbeat, reader.ReadByte());
                    Assert.AreEqual(messageData, reader.ReadByte());
                    Assert.AreEqual(ushort.MaxValue, reader.ReadUInt16BigEndian());
                });
        }

        [TestMethod]
        public void WhenTracingHeartbeatMessageCreated()
        {
            const byte messageData = 84;
            TestMessage((writer, protocol) => protocol.WriteHeartbeat(writer, AgentOperationMode.Tracing, ushort.MaxValue),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.Heartbeat, reader.ReadByte());
                    Assert.AreEqual(messageData, reader.ReadByte());
                    Assert.AreEqual(ushort.MaxValue, reader.ReadUInt16BigEndian());
                });
        }

        [TestMethod]
        public void WhenShutdownHeartbeatMessageCreated()
        {
            const byte messageData = 88;
            TestMessage((writer, protocol) => protocol.WriteHeartbeat(writer, AgentOperationMode.Shutdown, ushort.MaxValue),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.Heartbeat, reader.ReadByte());
                    Assert.AreEqual(messageData, reader.ReadByte());
                    Assert.AreEqual(ushort.MaxValue, reader.ReadUInt16BigEndian());
                });
        }

        [TestMethod]
        public void WhenWriteDataBreakMessageCreated()
        {
            const byte sequenceId = 1;
            TestMessage((writer, protocol) => protocol.WriteDataBreak(writer, sequenceId),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.DataBreak, reader.ReadByte());
                    Assert.AreEqual(sequenceId, reader.ReadInt32BigEndian());
                });
        }

        [TestMethod]
        public void WhenWriteMapMethodSignatureMessageCreated()
        {
            const byte signatureId = 1;
            const string signature = "Foo";
            TestMessage((writer, protocol) => protocol.WriteMapMethodSignature(writer, signatureId, signature),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.MapMethodSignature, reader.ReadByte());
                    Assert.AreEqual(signatureId, reader.ReadInt32BigEndian());
                    Assert.AreEqual(signature, reader.ReadUtfBigEndian());
                });
        }

        [TestMethod]
        public void WhenWriteMethodEntryMessageCreated()
        {
            const int relTime = 1;
            const int seq = 2;
            const int sigId = 3;
            const ushort threadId = 4;
            TestMessage((writer, protocol) => protocol.WriteMethodEntry(writer, relTime, seq, sigId, threadId),
                (reader, protocol) =>
                {
                    Assert.AreEqual(MessageTypes.MethodEntry, reader.ReadByte());
                    Assert.AreEqual(relTime, reader.ReadInt32BigEndian());
                    Assert.AreEqual(seq, reader.ReadInt32BigEndian());
                    Assert.AreEqual(sigId, reader.ReadInt32BigEndian());
                    Assert.AreEqual(threadId, reader.ReadUInt16BigEndian());
                });
        }

        private void TestMessage(Action<BinaryWriter, IMessageProtocol> writeMessageAction, Action<BinaryReader, IMessageProtocol> readMessageAction)
        {
            // arrange
            var messageProtocol = new MessageProtocol();

            using (var memoryStream = new MemoryStream())
            using (var binaryReader = new BinaryReader(memoryStream))
            using (var binaryWriter = new BinaryWriter(memoryStream))
            {
                // act
                writeMessageAction(binaryWriter, messageProtocol);

                // assert
                binaryReader.BaseStream.Position = 0;
                readMessageAction(binaryReader, messageProtocol);
            }
        }
    }
}

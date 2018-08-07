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
using CodePulse.Client.Util;

namespace CodePulse.Client.Message
{
    public class MessageProtocol : IMessageProtocol
    {
        public byte ProtocolVersion { get; } = 3;

        public void WriteHello(BinaryWriter writer)
        {
            writer.Write(MessageTypes.Hello);
            writer.Write(ProtocolVersion);
        }

        public void WriteDataHello(BinaryWriter writer, byte runId)
        {
            writer.Write(MessageTypes.DataHello);
            writer.Write(runId);
        }

        public void WriteError(BinaryWriter writer, string error)
        {
            writer.Write(MessageTypes.Error);
            writer.WriteUtfBigEndian(error);
        }

        public void WriteHeartbeat(BinaryWriter writer, AgentOperationMode mode, ushort sendBufferSize)
        {
            writer.Write(MessageTypes.Heartbeat);
            switch (mode)
            {
                case AgentOperationMode.Initializing:
                    writer.Write((byte) 73);
                    break;
                case AgentOperationMode.Paused:
                    writer.Write((byte) 80);
                    break;
                case AgentOperationMode.Suspended:
                    writer.Write((byte) 83);
                    break;
                case AgentOperationMode.Tracing:
                    writer.Write((byte) 84);
                    break;
                case AgentOperationMode.Shutdown:
                    writer.Write((byte) 88);
                    break;
                default:
                    throw new InvalidOperationException($"Unable to write heatbeat for unknown AgentOperationMode: {mode}.");
            }
            writer.WriteBigEndian(sendBufferSize);
        }

        public void WriteDataBreak(BinaryWriter writer, int sequenceId)
        {
            writer.Write(MessageTypes.DataBreak);
            writer.WriteBigEndian(sequenceId);
        }

        public void WriteMapMethodSignature(BinaryWriter writer, int sigId, string signature)
        {
            writer.Write(MessageTypes.MapMethodSignature);
            writer.WriteBigEndian(sigId);
            writer.WriteUtfBigEndian(signature);
        }

        public void WriteMethodEntry(BinaryWriter writer, int relTime, int seq, int sigId, ushort threadId)
        {
            writer.Write(MessageTypes.MethodEntry);
            writer.WriteBigEndian(relTime);
            writer.WriteBigEndian(seq);
            writer.WriteBigEndian(sigId);
            writer.WriteBigEndian(threadId);
        }

	    public void WriteMapSourceLocation(BinaryWriter writer, int sourceLocationId, int sigId, int startLine, int endLine, short startCharacter, short endCharacter)
	    {
			writer.Write(MessageTypes.MapSourceLocation);
		    writer.WriteBigEndian(sourceLocationId);
		    writer.WriteBigEndian(sigId);
		    writer.WriteBigEndian(startLine);
		    writer.WriteBigEndian(endLine);
		    writer.WriteBigEndian(startCharacter);
		    writer.WriteBigEndian(endCharacter);
		}

		public void WriteMethodVisit(BinaryWriter writer, int relTime, int seq, int sigId, int sourceLocationId,
		    ushort threadId)
	    {
			writer.Write(MessageTypes.MethodVisit);
		    writer.WriteBigEndian(relTime);
		    writer.WriteBigEndian(seq);
		    writer.WriteBigEndian(sigId);
		    writer.WriteBigEndian(sourceLocationId);
			writer.WriteBigEndian(threadId);
		}

	}
}

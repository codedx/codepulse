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
using CodePulse.Client.Connect;
using CodePulse.Client.Message;
using CodePulse.Client.Util;

namespace CodePulse.Client.Init
{
    public class DataConnectionHandshake : IDataConnectionHandshake
    {
        private readonly IMessageProtocol _messageProtocol;

        public DataConnectionHandshake(IMessageProtocol messageProtocol)
        {
            _messageProtocol = messageProtocol ?? throw new ArgumentNullException(nameof(messageProtocol));
        }

        public bool PerformHandshake(byte runId, IConnection connection)
        {
            if (connection == null)
            {
                throw new ArgumentNullException(nameof(connection));
            }

            var outputWriter = connection.OutputWriter;

            _messageProtocol.WriteDataHello(outputWriter, runId);
            outputWriter.FlushAndLog("WriteDataHello");

            var inputReader = connection.InputReader;
            var reply = inputReader.ReadByte();

            switch (reply)
            {
                case MessageTypes.DataHelloReply:
                    return true;
                case MessageTypes.Error:
                    throw new HandshakeException(inputReader.ReadUtfBigEndian(), reply);
                default:
                    throw new HandshakeException($"Handshake operation failed with unexpected reply: {reply}", reply);
            }
        }
    }
}

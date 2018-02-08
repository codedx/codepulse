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
using System.Net.Sockets;

namespace CodePulse.Client.Util
{
    public class SocketFactory
    {
        public int Port { get; }

        public int RetryDurationInMilliseconds { get; }

        public string Host { get; }

        public SocketFactory(string host, int port, int retryDurationInMilliseconds)
        {
            Host = host ?? throw new ArgumentNullException(nameof(host));
            if (port <= 0)
            {
                throw new ArgumentOutOfRangeException(nameof(port));
            }
            if (retryDurationInMilliseconds < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(retryDurationInMilliseconds));
            }
            Port = port;
            RetryDurationInMilliseconds = retryDurationInMilliseconds;
        }

        public Socket Connect()
        {
            var now = DateTime.UtcNow;
            var timeoutExpires = now.AddMilliseconds(RetryDurationInMilliseconds);

            Socket socket;
            do
            {
                socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
                try
                {
                    socket.Connect(Host, Port);
                }
                catch
                {
                    socket.Dispose();
                    socket = null;
                }
            }
            while (socket == null && DateTime.UtcNow < timeoutExpires);

            return socket;
        }
    }
}

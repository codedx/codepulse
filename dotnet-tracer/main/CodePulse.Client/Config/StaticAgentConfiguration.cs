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
using log4net;

namespace CodePulse.Client.Config
{
    public class StaticAgentConfiguration
    {
        public const int DefaultConnectTimeout = 30;

        public int HqPort { get; }

        public string HqHost { get; }

        public int ConnectTimeout { get; }

        public ILog Logger { get; }

        public StaticAgentConfiguration(int hqPort,
            string hqHost,
            int connectTimeout,
            ILog logger)
        {
            if (hqHost == null)
            {
                throw new ArgumentNullException(nameof(hqHost));
            }
            if (string.IsNullOrWhiteSpace(hqHost))
            {
                throw new ArgumentException("Value cannot be null or whitespace.", nameof(hqHost));
            }
            if (connectTimeout < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(connectTimeout));
            }

            HqPort = hqPort;
            HqHost = hqHost;
            ConnectTimeout = connectTimeout;
            Logger = logger ?? throw new ArgumentNullException(nameof(logger));
        }
    }
}

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

namespace CodePulse.Client.Message
{
    public abstract class MessageTypes
    {
        public const byte Hello = 0;
        public const byte Configuration = 1;
        public const byte Start = 2;
        public const byte Stop = 3;
        public const byte Pause = 4;
        public const byte Unpause = 5;
        public const byte Suspend = 6;
        public const byte Unsuspend = 7;
        public const byte Heartbeat = 8;
        public const byte DataBreak = 9;
        public const byte MapThreadName = 10;
        public const byte MapMethodSignature = 11;
        public const byte MapException = 12;

        public const byte MethodEntry = 20;
        public const byte MethodExit = 21;
        public const byte Exception = 22;
        public const byte ExceptionBubble = 23;

        public const byte DataHello = 30;
        public const byte DataHelloReply = 31;

        public const byte ClassTransformed = 40;
        public const byte ClassIgnored = 41;
        public const byte ClassTransformFailed = 42;

        public const byte Marker = 50;

        public const byte Error = 99;
    }
}

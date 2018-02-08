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
using System.Threading;
using CodePulse.Client.Errors;
using CodePulse.Client.Message;
using CodePulse.Client.Queue;
using log4net;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Moq;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class PooledMessageSenderTests
    {
        [TestMethod]
        public void WhenSenderStartsItRunsUntilShutdown()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9);
            var binaryWriter = new Mock<BinaryWriter>();
            var errorHandler = new Mock<IErrorHandler>();
            var logger = new Mock<ILog>();

            // act
            var messageSender = new PooledMessageSender(bufferPool, binaryWriter.Object, errorHandler.Object, logger.Object);

            Thread.Sleep(5000);
            messageSender.Shutdown();
            Thread.Sleep(5000);

            // assert
            Assert.IsTrue(messageSender.IsIdle);
            Assert.IsTrue(messageSender.IsShutdown);
        }
    }
}

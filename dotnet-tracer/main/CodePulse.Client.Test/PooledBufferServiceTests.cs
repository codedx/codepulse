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
using System.Threading.Tasks;
using CodePulse.Client.Message;
using CodePulse.Client.Queue;
using log4net;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Moq;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class PooledBufferServiceTests
    {
        [TestMethod]
        public void WhenServiceIsSuspendedObtainBufferReturnsNull()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9, new Mock<ILog>().Object);
            var service = new PooledBufferService(bufferPool);

            // act
            service.SetSuspended(true);

            var buffer = service.ObtainBuffer();

            // assert
            Assert.IsNull(buffer);
        }

        [TestMethod]
        public void WhenServiceIsPausedObtainBufferBlocks()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9, new Mock<ILog>().Object);
            var service = new PooledBufferService(bufferPool);

            // act
            service.SetPaused(true);

            var task1 = Task.Run(() =>
            {
                service.ObtainBuffer();
            });
            var task2 = Task.Run(() =>
            {
                service.ObtainBuffer();
            });

            var waitResult1 = task1.Wait(TimeSpan.FromMilliseconds(2000));
            var waitResult2 = task2.Wait(TimeSpan.FromMilliseconds(2000));

            // assert
            Assert.IsFalse(waitResult1);
            Assert.IsFalse(waitResult2);

            service.SetPaused(false);

            waitResult1 = task1.Wait(TimeSpan.FromMilliseconds(2000));
            waitResult2 = task2.Wait(TimeSpan.FromMilliseconds(2000));

            Assert.IsTrue(waitResult1);
            Assert.IsTrue(waitResult2);
        }

        [TestMethod]
        public void WhenServiceIsNotSuspendedBufferIsReturned()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9, new Mock<ILog>().Object);
            var service = new PooledBufferService(bufferPool);

            // act
            var buffer = service.ObtainBuffer();

            // assert
            Assert.IsNotNull(buffer);
        }

        [TestMethod]
        public void WhenBufferRelinquishedReadableBufferCountIncreases()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9, new Mock<ILog>().Object);
            var service = new PooledBufferService(bufferPool);
            var readableBuffersBefore = bufferPool.ReadableBuffers;

            // act
            var buffer = service.ObtainBuffer();
            buffer.WriteByte(1);
            service.RelinquishBuffer(buffer);

            // assert
            Assert.IsNotNull(buffer);
            Assert.AreEqual(0, readableBuffersBefore);
            Assert.AreEqual(1, bufferPool.ReadableBuffers);
        }
    }
}

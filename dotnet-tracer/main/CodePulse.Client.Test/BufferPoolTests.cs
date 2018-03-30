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
using System.Threading.Tasks;
using CodePulse.Client.Queue;
using CodePulse.Client.Util;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class BufferPoolTests
    {
        [TestMethod]
        public void WhenDataReadTheTotalReadableBuffersCountIsZero()
        {
            // arrange
            var bufferPool = new BufferPool(1, 9);
            var memoryStream = bufferPool.AcquireForWriting();
            memoryStream.Write(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, 0, 9);
            bufferPool.Release(memoryStream);

            var readableBuffersBefore = bufferPool.ReadableBuffers;

            // act
            var writableStream = bufferPool.AcquireForReading(TimeSpan.MaxValue);

            var buffer = new byte[9];
            using (var bufferStream = new MemoryStream(buffer))
            {
                writableStream.WriteTo(bufferStream);
                writableStream.Reset();
            }
            bufferPool.Release(writableStream);

            // assert
            Assert.AreEqual(1, readableBuffersBefore);
            Assert.AreEqual(0, bufferPool.ReadableBuffers);
        }

        [TestMethod]
        public void WhenOneBufferIsFullOtherPartialBufferIsNotReturnedForReading()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9);

            var fullBuffer = bufferPool.AcquireForWriting();
            fullBuffer.Write(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, 0, 9);
            bufferPool.Release(fullBuffer);

            var partialBuffer = bufferPool.AcquireForWriting();
            partialBuffer.Write(new byte[] {0, 1, 2}, 0, 3);
            bufferPool.Release(partialBuffer);

            // act
            var buffer = bufferPool.AcquireForReading(TimeSpan.MaxValue);

            // assert
            Assert.AreSame(fullBuffer, buffer);
        }

        [TestMethod]
        public void WhenOneBufferIsFullOtherPartialBufferReturnedForWriting()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9);

            var fullBuffer = bufferPool.AcquireForWriting();
            fullBuffer.Write(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, 0, 9);
            bufferPool.Release(fullBuffer);

            var partialBuffer = bufferPool.AcquireForWriting();
            partialBuffer.Write(new byte[] {0, 1, 2}, 0, 3);
            bufferPool.Release(partialBuffer);

            // act
            var buffer = bufferPool.AcquireForWriting();

            // assert
            Assert.AreSame(partialBuffer, buffer);
        }

        [TestMethod]
        public void WhenAllBuffersReadAndReturnedPoolIsEmpty()
        {
            // arrange
            var bufferPool = new BufferPool(2, 9);

            var fullBuffer = bufferPool.AcquireForWriting();
            fullBuffer.Write(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, 0, 9);
            bufferPool.Release(fullBuffer);

            var partialBuffer = bufferPool.AcquireForWriting();
            partialBuffer.Write(new byte[] {0, 1, 2}, 0, 3);
            bufferPool.Release(partialBuffer);

            // act
            var fullBufferFromPool = bufferPool.AcquireForReading(TimeSpan.MaxValue);
            fullBufferFromPool.Reset();
            bufferPool.Release(fullBufferFromPool);

            var partialBufferFromPool = bufferPool.AcquireForReading(TimeSpan.MaxValue);
            partialBufferFromPool.Reset();
            bufferPool.Release(partialBufferFromPool);

            // assert
            Assert.IsTrue(bufferPool.IsEmpty);
        }

        [TestMethod]
        public void WhenWriteDisabledCannotAcquireForWriting()
        {
            // arrange
            var bufferPool = new BufferPool(1, 10);

            var memoryStream = bufferPool.AcquireForWriting();
            memoryStream.WriteByte(1);
            bufferPool.Release(memoryStream);

            bufferPool.SetWriteDisabled(true);

            // act
            var writableStream = bufferPool.AcquireForWriting();

            // assert
            Assert.IsNull(writableStream);
            Assert.AreEqual(1, bufferPool.ReadableBuffers);
        }

        [TestMethod]
        public void WhenAllBuffersFilledBlockingOccurs()
        {
            // arrange
            var bufferPool = new BufferPool(2, 5);

            var stream1 = bufferPool.AcquireForWriting();
            stream1.Write(new byte[] {0, 1, 2, 3, 4}, 0, 5);
            bufferPool.Release(stream1);

            var stream2 = bufferPool.AcquireForWriting();
            stream2.Write(new byte[] {0, 1, 2, 3, 4}, 0, 5);
            bufferPool.Release(stream2);

            // act
            var task = Task.Run(() =>
            {
                bufferPool.AcquireForWriting();
            });
            var waitResult = task.Wait(TimeSpan.FromMilliseconds(2000));

            // assert
            Assert.IsFalse(waitResult);
        }

        [TestMethod]
        public void WhenNoDataBlockingOccurs()
        {
            // arrange
            var bufferPool = new BufferPool(2, 5);

            // act
            var task = Task.Run(() =>
            {
                bufferPool.AcquireForReading(TimeSpan.MaxValue);
            });
            var waitResult = task.Wait(TimeSpan.FromMilliseconds(2000));

            // assert
            Assert.IsFalse(waitResult);
        }

        [TestMethod]
        public void WhenDataBecomesAvailableBlockingClears()
        {
            // arrange
            var bufferPool = new BufferPool(2, 5);

            // act
            var task = Task.Run(() =>
            {
                bufferPool.AcquireForReading(TimeSpan.MaxValue);
            });
            var waitResult = task.Wait(TimeSpan.FromMilliseconds(2000));

            // assert
            Assert.IsFalse(waitResult);

            var stream = bufferPool.AcquireForWriting();
            stream.WriteByte(1);
            bufferPool.Release(stream);

            waitResult = task.Wait(TimeSpan.FromMilliseconds(2000));
            Assert.IsTrue(waitResult);
        }
    }
}

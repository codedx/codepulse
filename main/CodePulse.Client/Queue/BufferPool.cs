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
using System.Collections.Concurrent;
using System.IO;

namespace CodePulse.Client.Queue
{
    public class BufferPool
    {
        private readonly BlockingCollection<MemoryStream> _emptyBuffers;
        private readonly BlockingCollection<MemoryStream> _partialBuffers;
        private readonly BlockingCollection<MemoryStream> _fullBuffers;

        private readonly int _fullThreshold;
        private volatile bool _writeDisabled;

        public int ReadableBuffers => _fullBuffers.Count + _partialBuffers.Count;

        public bool IsEmpty => _emptyBuffers.Count == _emptyBuffers.BoundedCapacity &&
                               _partialBuffers.Count == 0 && _fullBuffers.Count == 0;

        public BufferPool(int numBuffers, int initialBufferCapacity)
        {
            _fullThreshold = (int) (initialBufferCapacity * 0.9);

            _emptyBuffers = new BlockingCollection<MemoryStream>(numBuffers);
            _partialBuffers = new BlockingCollection<MemoryStream>(numBuffers);
            _fullBuffers = new BlockingCollection<MemoryStream>(numBuffers);

            for (var i = 0; i < numBuffers; i++)
            {
                _emptyBuffers.Add(new MemoryStream(initialBufferCapacity));
            }
        }

        public MemoryStream AcquireForWriting()
        {
            while (true)
            {
                if (_writeDisabled)
                {
                    return null;
                }

                if (_partialBuffers.TryTake(out MemoryStream partialStream))
                {
                    return partialStream;
                }

                if (_emptyBuffers.TryTake(out MemoryStream emptyStream, 1))
                {
                    return emptyStream;
                }
            }
        }

        public MemoryStream AcquireForReading(TimeSpan timeout)
        {
            var now = DateTime.UtcNow;
            while (timeout == TimeSpan.MaxValue || DateTime.UtcNow.Subtract(now).TotalMilliseconds < timeout.TotalMilliseconds)
            {
                if (_fullBuffers.TryTake(out MemoryStream fullStream))
                {
                    return fullStream;
                }

                if (_partialBuffers.TryTake(out MemoryStream partialStream, 1))
                {
                    return partialStream;
                }
            }
            return null;
        }

        public void Release(MemoryStream stream)
        {
            var bufferSize = stream.Length;
            if (bufferSize == 0)
            {
                _emptyBuffers.Add(stream);
            }
            else if (bufferSize < _fullThreshold)
            {
                _partialBuffers.Add(stream);
            }
            else
            {
                _fullBuffers.Add(stream);
            }
        }

        public void SetWriteDisabled(bool writeDisabled)
        {
            _writeDisabled = writeDisabled;
        }
    }
}

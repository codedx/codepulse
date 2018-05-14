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
using log4net;

namespace CodePulse.Client.Queue
{
    public class BufferPool
    {
	    private readonly object _readLock = new object();

		private readonly BlockingCollection<NamedMemoryStream> _emptyBuffers;
        private readonly BlockingCollection<NamedMemoryStream> _partialBuffers;
        private readonly BlockingCollection<NamedMemoryStream> _fullBuffers;

        private readonly int _fullThreshold;
	    private readonly ILog _logger;

        private volatile bool _writeDisabled;

        public int ReadableBuffers => _fullBuffers.Count + _partialBuffers.Count;

        public bool IsEmpty => _emptyBuffers.Count == _emptyBuffers.BoundedCapacity &&
                               _partialBuffers.Count == 0 && _fullBuffers.Count == 0;

        public BufferPool(int numBuffers, int initialBufferCapacity, ILog logger)
        {
	        _fullThreshold = (int) (initialBufferCapacity * 0.9);
	        _logger = logger;

            _emptyBuffers = new BlockingCollection<NamedMemoryStream>(numBuffers);
            _partialBuffers = new BlockingCollection<NamedMemoryStream>(numBuffers);
            _fullBuffers = new BlockingCollection<NamedMemoryStream>(numBuffers);

            for (var i = 0; i < numBuffers; i++)
            {
	            var stream = new NamedMemoryStream(initialBufferCapacity);

				_logger.DebugFormat("Created stream {0}", stream.Name);
				_emptyBuffers.Add(stream);
            }
        }

        public NamedMemoryStream AcquireForWriting()
        {
            while (true)
            {
                if (_writeDisabled)
                {
                    return null;
                }

                if (_partialBuffers.TryTake(out var partialStream))
                {
	                _logger.DebugFormat("Partial stream {0} acquired for writing", partialStream.Name);
					return partialStream;
                }

                if (_emptyBuffers.TryTake(out var emptyStream, 1))
                {
	                _logger.DebugFormat("Empty stream {0} acquired for writing", emptyStream.Name);
					return emptyStream;
                }
            }
        }

        public NamedMemoryStream AcquireForReading(TimeSpan timeout)
        {
            var now = DateTime.UtcNow;
            while (timeout == TimeSpan.MaxValue || DateTime.UtcNow.Subtract(now).TotalMilliseconds < timeout.TotalMilliseconds)
            {
    	        lock (_readLock) // avoid potential race condition where partial buffer read before full buffer
    	        {
			        if (_fullBuffers.TryTake(out var fullStream))
			        {
						_logger.DebugFormat("Full stream {0} acquired for reading", fullStream.Name);
						return fullStream;
			        }

			        if (_partialBuffers.TryTake(out var partialStream, 1))
			        {
						_logger.DebugFormat("Partial stream {0} acquired for reading", partialStream.Name);
						return partialStream;
			        }
		        }
	        }
	        return null;
		}

		public void Release(NamedMemoryStream stream)
        {
            var bufferSize = stream.Length;
            if (bufferSize == 0)
            {
                _emptyBuffers.Add(stream);
                _logger.DebugFormat("Stream {0} returned empty", stream.Name);
            }
            else if (bufferSize < _fullThreshold)
            {
			    lock (_readLock)
			    {
				    _partialBuffers.Add(stream);
				}
			    _logger.DebugFormat("Stream {0} returned partially full", stream.Name);
            }
            else
            {
			    lock (_readLock)
			    {
				    _fullBuffers.Add(stream);
			    }
			    _logger.DebugFormat("Stream {0} returned full", stream.Name);
            }
        }

        public void SetWriteDisabled(bool writeDisabled)
        {
            _writeDisabled = writeDisabled;
        }
    }
}

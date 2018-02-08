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
using System.Threading;

namespace CodePulse.Client.Message
{
    public abstract class BufferService
    {
        private readonly object _pauseObject = new object();

        private volatile bool _paused;
        private volatile bool _suspended;

        public bool IsPaused => _paused;

        public bool IsSuspended => _suspended;

        public MemoryStream ObtainBuffer()
        {
            BlockWhilePaused();

            return _suspended ? null : OnObtainBuffer();
        }

        public void RelinquishBuffer(MemoryStream stream)
        {
            if (stream == null) throw new ArgumentNullException(nameof(stream));

            OnRelinquishBuffer(stream);
        }

        public void SetPaused(bool paused)
        {
            _paused = paused;

            if (_paused)
            {
                return;
            }

            lock (_pauseObject)
            {
                Monitor.PulseAll(_pauseObject);
            }
        }

        public virtual void SetSuspended(bool suspended)
        {
            _suspended = suspended;
        }

        protected abstract MemoryStream OnObtainBuffer();

        protected abstract void OnRelinquishBuffer(MemoryStream stream);

        private void BlockWhilePaused()
        {
            while (_paused)
            {
                lock (_pauseObject)
                {
                    Monitor.Wait(_pauseObject);
                }
            }
        }
    }
}

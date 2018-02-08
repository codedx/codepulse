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

using System.Collections.Generic;

namespace CodePulse.Client.Instrumentation.Id
{
    public class MethodIdentifier
    {
        private int _nextMethodId;

        private readonly Dictionary<int, MethodInformation> _methodsById = new Dictionary<int, MethodInformation>();
        private readonly Dictionary<string, MethodInformation> _methodsBySignature = new Dictionary<string, MethodInformation>();

        public int Record(int classId, string name, string signature, int startLine, int endLine)
        {
            if (_methodsBySignature.TryGetValue(signature, out var methodInformation))
            {
                return methodInformation.Id;
            }

            var methodId = _nextMethodId++;
            var newMethodInformation = new MethodInformation(methodId, classId, name, signature, startLine, endLine);

            _methodsById[methodId] = newMethodInformation;
            _methodsBySignature[signature] = newMethodInformation;

            return methodId;
        }

        public MethodInformation Lookup(int methodId)
        {
            return !_methodsById.TryGetValue(methodId, out var info) ? null : info;
        }
    }
}

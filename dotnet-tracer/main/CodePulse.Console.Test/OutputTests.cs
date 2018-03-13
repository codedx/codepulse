// Copyright 2018 Secure Decisions, a division of Applied Visions, Inc. 
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
using System.Diagnostics;
using System.IO;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace CodePulse.Console.Test
{
    [TestClass]
    public class OutputTests
    {
        [TestMethod]
        public void CodePulseConsoleOutputHasPreferred32BitEnabled()
        {
            OutputHasPreferred32BitEnabled("CodePulse.DotNet.Tracer.exe");
        }

        private void OutputHasPreferred32BitEnabled(string filename)
        {
            var pi = new ProcessStartInfo
            {
                FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Microsoft SDKs\Windows\v10.0A\bin\NETFX 4.7.1 Tools\corflags.exe"),
                Arguments = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, filename),
                CreateNoWindow = true,
                UseShellExecute = false,
                RedirectStandardOutput = true
            };

            if (!File.Exists(pi.Arguments))
            {
                Assert.Inconclusive($"Unable to find exe at {pi.Arguments}.");
            }

            var process = Process.Start(pi);
            Assert.IsNotNull(process);
            var output = process.StandardOutput.ReadToEnd();
            process.WaitForExit();
            System.Console.WriteLine(output);

            Assert.IsTrue(output.Contains("32BITREQ  : 0"));
            Assert.IsTrue(output.Contains("32BITPREF : 1"));
        }
    }
}

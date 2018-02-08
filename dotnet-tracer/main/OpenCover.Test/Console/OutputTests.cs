//
// Modified work Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
//

using System;
using System.Diagnostics;
using System.IO;
using NUnit.Framework;

// ReSharper disable once CheckNamespace
namespace OpenCover.Test.ConsoleEx
{
    [TestFixture]
    public class OutputTests
    {
        [Test]
        public void OutputHasPreferred32BitDisabled()
        {
            var pi = new ProcessStartInfo()
            {
                FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Microsoft SDKs\Windows\v10.0A\bin\NETFX 4.7.1 Tools\corflags.exe"),
                Arguments = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "OpenCover.Console.exe"),
                CreateNoWindow = true,
                UseShellExecute = false,
                RedirectStandardOutput = true
            };

            var process = Process.Start(pi);
            Assert.IsNotNull(process);
            var output = process.StandardOutput.ReadToEnd();
            process.WaitForExit();
            Console.WriteLine(output);

            Assert.IsTrue(output.Contains("32BITPREF : 0"));
        }
    }
}

//
// Modified work Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
//

using System;
using System.IO;
using Mono.Cecil.Cil;
using Mono.Cecil.Pdb;
using Moq;
using NUnit.Framework;
using OpenCover.Framework;
using OpenCover.Framework.Symbols;

namespace OpenCover.Test.Framework.Symbols
{
    [TestFixture]
    public class SymbolFileHelperTests
    {
        [Test]
        public void CanFindAndLoadProviderForPdbFile()
        {
            var commandLine = new Mock<ICommandLine>();
            var assemblyPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Unity.ServiceLocation.dll");

            var symbolFile = SymbolFileHelper.FindSymbolFolder(assemblyPath, commandLine.Object);

            Assert.NotNull(symbolFile);
            Assert.IsInstanceOf<PortablePdbReaderProvider>(symbolFile.SymbolReaderProvider);
            Assert.IsTrue(symbolFile.SymbolFilename.EndsWith(".pdb", StringComparison.InvariantCultureIgnoreCase));
        }
    }
}

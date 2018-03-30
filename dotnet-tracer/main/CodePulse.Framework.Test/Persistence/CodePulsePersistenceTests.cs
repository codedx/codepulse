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
using System.Collections.Generic;
using CodePulse.Client.Agent;
using CodePulse.Client.Trace;
using CodePulse.Framework.Persistence;
using log4net;
using Moq;
using NUnit.Framework;
using OpenCover.Framework;
using OpenCover.Framework.Model;

namespace CodePulse.Framework.Test.Persistence
{
    [TestFixture]
    public class CodePulsePersistenceTests
    {
        private Mock<ICommandLine> _mockCommandLine;
        private Mock<ILog> _mockLogger;
        private Mock<ITraceAgent> _traceAgent;
        private Mock<ITraceDataCollector> _traceDataCollector;

        [SetUp]
        public void SetUp()
        {
            _mockCommandLine = new Mock<ICommandLine>();
            _mockLogger = new Mock<ILog>();
            _traceAgent = new Mock<ITraceAgent>();
            _traceDataCollector = new Mock<ITraceDataCollector>();
            _traceAgent.Setup(x => x.TraceDataCollector).Returns(_traceDataCollector.Object);
        }

        [Test]
        public void WhenCommitOccursWithoutContextEnd_TraceDataAdded()
        {
            // arrange
            const string className = "ClassName";
            const string classFilename = "ClassName.cs";
            const string methodSignature = "void ClassName::MethodName();";
            const int startLine = 1;
            const int endLine = 1;

            var module = new Module
            {
                ModuleName = "ModuleName",
                ModulePath = @"C:\Module.dll",
                Aliases = { @"C:\Module.dll" },
                Classes = new[]
                {
                    new Class
                    {
                        FullName = className,
                        Files = new [] { new File{ FullPath = classFilename } },
                        Methods = new[]
                        {
                            new Method
                            {
                                FullName = "MethodName",
                                MethodSignature = methodSignature,
                                MetadataToken = 1
                            }
                        }
                    }
                }
            };

            module.Classes[0].Methods[0].DeclaringClass = module.Classes[0];
            module.Classes[0].DeclaringModule = module;

            _traceAgent.Setup(x => x.Connect()).Returns(true);
            _traceAgent.Setup(x => x.Prepare()).Returns(true);

            _traceDataCollector.Setup(x => x.AddMethodVisit(
                className,
                classFilename,
                "",
                methodSignature,
                startLine,
                endLine));

            var persistence = new CodePulsePersistence(_mockCommandLine.Object, _mockLogger.Object);
            persistence.Initialize(_traceAgent.Object);

            persistence.PersistModule(module);

            var instrumentationPoint = new SequencePoint
            {
                DeclaringMethod = module.Classes[0].Methods[0],
                StartLine = startLine,
                EndLine = endLine
            };

            var visitData = new List<byte>();
            visitData.AddRange(BitConverter.GetBytes(5u));
            visitData.AddRange(BitConverter.GetBytes(1u));
            visitData.AddRange(BitConverter.GetBytes(2ul));
            visitData.AddRange(BitConverter.GetBytes(3ul));

            // act
            persistence.SaveVisitData(visitData.ToArray());
            persistence.Commit();

            // assert
            Assert.AreEqual(1, instrumentationPoint.UniqueSequencePoint);
            _traceDataCollector.Verify(x => x.AddMethodVisit(className,
                classFilename,
                "",
                methodSignature,
                startLine,
                endLine));
        }
    }
}

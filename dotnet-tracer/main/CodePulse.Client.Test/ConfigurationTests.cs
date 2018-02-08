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

using System.IO;
using CodePulse.Client.Control;
using CodePulse.Client.Util;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class ConfigurationTests
    {
        [TestMethod]
        public void WhenDeserializedRuntimeDataConfigurationIsCorrect()
        {
            // arrange
            var configurationReader = new ConfigurationReader();

            using (var outputStream = new MemoryStream())
            using (var binaryWriter = new BinaryWriter(outputStream))
            {
                binaryWriter.WriteUtfBigEndian("{\"RunId\":1,\"HeartbeatInterval\":2,\"Exclusions\":[\"Exclusion\"],\"Inclusions\":[\"Inclusion\"],\"BufferMemoryBudget\":3,\"QueueRetryCount\":4,\"NumDataSenders\":5}");

                using (var inputStream = new MemoryStream(outputStream.ToArray()))
                using (var binaryReader = new BinaryReader(inputStream))
                {
                    // act
                    var configuration = configurationReader.ReadConfiguration(binaryReader);

                    // assert
                    Assert.AreEqual(1, configuration.RunId);
                    Assert.AreEqual(2, configuration.HeartbeatInterval);
                    Assert.AreEqual("Exclusion", configuration.Exclusions[0]);
                    Assert.AreEqual("Inclusion", configuration.Inclusions[0]);
                    Assert.AreEqual(3, configuration.BufferMemoryBudget);
                    Assert.AreEqual(5, configuration.NumDataSenders);
                }
            }
        }
    }
}

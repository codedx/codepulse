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

using CodePulse.Client.Trace;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Mono.Cecil;
using MethodAttributes = Mono.Cecil.MethodAttributes;
using ParameterAttributes = Mono.Cecil.ParameterAttributes;
using TypeAttributes = Mono.Cecil.TypeAttributes;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class MethodSignatureTests
    {
        [TestMethod]
        public void CanBuildSignatureWithNoParamsOrReturn()
        {
            // arrange
            var signatureBuilder = new MethodSignatureBuilder();
            var methodDefinition = new MethodDefinition("Foo", MethodAttributes.Public, new TypeDefinition("System", "Void", TypeAttributes.Public))
            {
                DeclaringType = new TypeDefinition("Namespace", "Class", TypeAttributes.Public)
            };

            // act
            var signature = signatureBuilder.CreateSignature(methodDefinition);

            // assert
            Assert.AreEqual("Namespace.Class.Foo;1;();System.Void", signature);
        }

        [TestMethod]
        public void CanBuildSignatureWithNoParams()
        {
            // arrange
            var signatureBuilder = new MethodSignatureBuilder();
            var methodDefinition = new MethodDefinition("Foo", MethodAttributes.Public, new TypeDefinition("System", "Int32", TypeAttributes.Public))
            {
                DeclaringType = new TypeDefinition("Namespace", "Class", TypeAttributes.Public)
            };

            // act
            var signature = signatureBuilder.CreateSignature(methodDefinition);

            // assert
            Assert.AreEqual("Namespace.Class.Foo;1;();System.Int32", signature);
        }

        [TestMethod]
        public void CanBuildSignatureWithOneParams()
        {
            // arrange
            var signatureBuilder = new MethodSignatureBuilder();
            var methodDefinition = new MethodDefinition("Foo", MethodAttributes.Public, new TypeDefinition("System", "Int32", TypeAttributes.Public))
            {
                DeclaringType = new TypeDefinition("Namespace", "Class", TypeAttributes.Public),
                Parameters = { new ParameterDefinition("bar", ParameterAttributes.None, new TypeDefinition("System", "Int32", TypeAttributes.Public))}
            };

            // act
            var signature = signatureBuilder.CreateSignature(methodDefinition);

            // assert
            Assert.AreEqual("Namespace.Class.Foo;1;(System.Int32);System.Int32", signature);
        }

        [TestMethod]
        public void CanBuildSignatureWithMultipleParams()
        {
            // arrange
            var signatureBuilder = new MethodSignatureBuilder();
            var methodDefinition = new MethodDefinition("Foo", MethodAttributes.Public, new TypeDefinition("System", "Int32", TypeAttributes.Public))
            {
                DeclaringType = new TypeDefinition("Namespace", "Class", TypeAttributes.Public),
                Parameters =
                {
                    new ParameterDefinition("bar1", ParameterAttributes.None, new TypeDefinition("System", "Int32", TypeAttributes.Public)),
                    new ParameterDefinition("bar2", ParameterAttributes.None, new TypeDefinition("System", "Int16", TypeAttributes.Public)),
                    new ParameterDefinition("bar3", ParameterAttributes.None, new TypeDefinition("System", "Boolean", TypeAttributes.Public))
                }
            };

            // act
            var signature = signatureBuilder.CreateSignature(methodDefinition);

            // assert
            Assert.AreEqual("Namespace.Class.Foo;1;(System.Int32,System.Int16,System.Boolean);System.Int32", signature);
        }
    }
}

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
using CodePulse.Client.Control;
using CodePulse.Client.Errors;
using CodePulse.Client.Message;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Moq;

namespace CodePulse.Client.Test
{
    [TestClass]
    public class StateManagerTests
    {
        [TestMethod]
        public void WhenStartedInInitializationMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // assert
            Assert.AreEqual(AgentOperationMode.Initializing, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenTriggerShutdownInShutdownMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.TriggerShutdown();

            // assert
            Assert.AreEqual(AgentOperationMode.Shutdown, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenOnStartOccursInTracingMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.ControlMessageHandler.OnStart();

            // assert
            Assert.AreEqual(AgentOperationMode.Tracing, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenOnStartOccursAfterSuspendInitializationInSuspendedMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.ControlMessageHandler.OnSuspend();
            stateManager.ControlMessageHandler.OnStart();

            // assert
            Assert.AreEqual(AgentOperationMode.Suspended, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenOnStopOccursInShutdownMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnStop();

            // assert
            Assert.AreEqual(AgentOperationMode.Shutdown, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenOnPauseOccursInPausedMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnPause();

            // assert
            Assert.AreEqual(AgentOperationMode.Paused, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenOnUnpauseOccursInTracingMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnPause();
            stateManager.ControlMessageHandler.OnUnpause();

            // assert
            Assert.AreEqual(AgentOperationMode.Tracing, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenOnSuspendOccursInSuspendedMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnSuspend();

            // assert
            Assert.AreEqual(AgentOperationMode.Suspended, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenOnUnsuspendOccursInTracingMode()
        {
            // arrange
            var stateManager = new StateManager(new ErrorHandler());

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnSuspend();
            stateManager.ControlMessageHandler.OnUnsuspend();

            // assert
            Assert.AreEqual(AgentOperationMode.Tracing, stateManager.CurrentMode);
        }

        [TestMethod]
        public void WhenErrorOccursInTracingMode()
        {
            // arrange
            var errorHandler = new Mock<IErrorHandler>();
            var stateManager = new StateManager(errorHandler.Object);

            // act
            stateManager.ControlMessageHandler.OnError("error");

            // assert
            errorHandler.Verify(x => x.HandleError("Error received from HQ (error)", It.IsAny<Exception>()), Times.Once());
        }

        [TestMethod]
        public void WhenInvalidPauseErrorOccurs()
        {
            // arrange
            var errorHandler = new Mock<IErrorHandler>();
            var stateManager = new StateManager(errorHandler.Object);

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnStop();
            stateManager.ControlMessageHandler.OnPause();

            // assert
            errorHandler.Verify(x => x.HandleError("Pause control message is only valid when tracing.", It.IsAny<Exception>()), Times.Once());
        }

        [TestMethod]
        public void WhenInvalidUnpauseErrorOccurs()
        {
            // arrange
            var errorHandler = new Mock<IErrorHandler>();
            var stateManager = new StateManager(errorHandler.Object);

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnUnpause();

            // assert
            errorHandler.Verify(x => x.HandleError("Unpause control message is only valid when paused.", It.IsAny<Exception>()), Times.Once());
        }

        [TestMethod]
        public void WhenInvalidSuspendErrorOccurs()
        {
            // arrange
            var errorHandler = new Mock<IErrorHandler>();
            var stateManager = new StateManager(errorHandler.Object);

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnStop();
            stateManager.ControlMessageHandler.OnSuspend();

            // assert
            errorHandler.Verify(x => x.HandleError("Suspend control message is only valid when tracing or initializing.", It.IsAny<Exception>()), Times.Once());
        }

        [TestMethod]
        public void WhenInvalidUnsuspendErrorOccurs()
        {
            // arrange
            var errorHandler = new Mock<IErrorHandler>();
            var stateManager = new StateManager(errorHandler.Object);

            // act
            stateManager.ControlMessageHandler.OnStart();
            stateManager.ControlMessageHandler.OnUnsuspend();

            // assert
            errorHandler.Verify(x => x.HandleError("Unsuspend control message is only valid when suspended or initializing.", It.IsAny<Exception>()), Times.Once());
        }
    }
}


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
using Microsoft.VisualStudio.TestTools.UnitTesting;
using OpenCover.Framework.Utility;

namespace CodePulse.Console.Test
{
    [TestClass]
    public class CommandLineParserTests
    {
        [TestMethod]
        public void WhenIisModeAndMissingParametersExceptionOccurs()
        {
            AssertRunningAsWindowsAdmin();

            // arrange
            var parameters = new[]
            {
                "-IIS"
            };
            ValidateCommandLineArguments(parameters, "The TargetDir argument is required.");
        }

        [TestMethod]
        public void WhenIisModeWithTargetDirAndMissingParametersExceptionOccurs()
        {
            AssertRunningAsWindowsAdmin();

            // arrange
            var parameters = new[]
            {
                "-IIS",
                "-TargetDir:folder"
            };
            ValidateCommandLineArguments(parameters, "The IISAppPoolIdentity argument is required.");
        }

        [TestMethod]
        public void WhenIisModeServiceControlTimeoutTooLowExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-IIS",
                "-IISAppPoolIdentity:Account",
                "-TargetDir:folder",
                "-ServiceControlTimeout:4"
            };
            ValidateCommandLineArguments(parameters, "The service control timeout must be a non-negative integer. The argument servicecontroltimeout must be between 5 and 60.");
        }

        [TestMethod]
        public void WhenIisModeServiceControlTimeoutTooHighExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-IIS",
                "-IISAppPoolIdentity:Account",
                "-TargetDir:folder",
                "-ServiceControlTimeout:61"
            };
            ValidateCommandLineArguments(parameters, "The service control timeout must be a non-negative integer. The argument servicecontroltimeout must be between 5 and 60.");
        }

        [TestMethod]
        public void WhenIisModeAndSendVisitPointsTimerSpecifiedExceptionOccurs()
        {
            AssertRunningAsWindowsAdmin();

            // arrange
            var parameters = new[]
            {
                "-IIS",
                "-IISAppPoolIdentity:Account",
                "-TargetDir:folder",
                "-SendVisitPointsTimerInterval:1"
            };
            ValidateCommandLineArguments(parameters, "SendVisitPointsTimerInterval argument is incompatible with -IIS switch.");
        }

        [TestMethod]
        public void WhenIisModeSpecifiedCorrectlySuccess()
        {
            AssertRunningAsWindowsAdmin();

            // arrange
            var parameters = new[]
            {
                "-IIS",
                "-IISAppPoolIdentity:Account",
                "-TargetDir:folder",
                "-ServiceControlTimeout:60"
            };
            ValidateCommandLineArguments(parameters);
        }


        [TestMethod]
        public void WhenAppModeSendVisitPointsTimerIntervalTooLowExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:-1"
            };
            ValidateCommandLineArguments(parameters, "The send visit points timer interval must be a non-negative integer. -1 is not a valid value for UInt32.");
        }

        [TestMethod]
        public void WhenAppModeSendVisitPointsTimerIntervalTooHighExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                $"-SendVisitPointsTimerInterval:{(60u * 60u * 1000u) + 1}"
            };
            ValidateCommandLineArguments(parameters, "The send visit points timer interval must be a non-negative integer. The argument sendvisitpointstimerinterval must be between 0 and 3600000");
        }

        [TestMethod]
        public void WhenAppModeSpecifiedWithNoSendVisitPointsTimerIntervalExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file"
            };
            ValidateCommandLineArguments(parameters, "SendVisitPointsTimerInterval command line argument must be specified as a number > 0.");
        }

        [TestMethod]
        public void WhenAppModeWithLogIncorrectExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:any"
            };
            ValidateCommandLineArguments(parameters, "'any' is an invalid value for log parameter.");
        }

        [TestMethod]
        public void WhenAppModeSpecifiedCorrectlySuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogOffSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:off"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogFatalSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:fatal"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogErrorSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:error"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogWarnSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:warn"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogInfoSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:info"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogDebugSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:debug"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogVerboseSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:verbose"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenAppModeWithLogAllSpecifiedSuccess()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-Log:all"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenProjectIdInvalidExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-ProjectId:not-an-id"
            };
            ValidateCommandLineArguments(parameters, "The Code Pulse project ID must be a valid identifier: not-an-id is not a valid value for Int32.");
        }

        [TestMethod]
        public void WhenProjectIdSpecifiedCorrectly()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-ProjectId:1"
            };
            ValidateCommandLineArguments(parameters);
        }

        [TestMethod]
        public void WhenProjectIdNegativeExceptionOccurs()
        {
            // arrange
            var parameters = new[]
            {
                "-Target:file",
                "-SendVisitPointsTimerInterval:45",
                "-ProjectId:-11"
            };
            ValidateCommandLineArguments(parameters, "The Code Pulse project ID must be a valid identifier: The argument projectid must be between 0 and 2147483647");
        }

        private static void ValidateCommandLineArguments(string[] parameters, string expectedError = "")
        {
            // arrange
            var parser = new CommandLineParser(parameters);

            // act
            var error = string.Empty;
            try
            {
                parser.ExtractAndValidateArguments();
            }
            catch (InvalidOperationException e)
            {
                error = e.Message;
            }

            // assert
            Assert.AreEqual(expectedError, error);
        }

        private static void AssertRunningAsWindowsAdmin()
        {
            if (!IdentityHelper.IsRunningAsWindowsAdmin())
            {
                Assert.Inconclusive("Skipping test because administrator privileges are missing.");
            }
        }
    }
}

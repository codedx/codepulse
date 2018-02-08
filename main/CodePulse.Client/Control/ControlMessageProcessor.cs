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
using CodePulse.Client.Errors;
using CodePulse.Client.Message;
using CodePulse.Client.Util;

namespace CodePulse.Client.Control
{
    public class ControlMessageProcessor : IControlMessageProcessor
    {
        private readonly IErrorHandler _errorHandler;
        private readonly IConfigurationReader _configurationReader;
        private readonly IControlMessageHandler _messageHandler;
        private readonly IConfigurationHandler _configurationHandler;

        public ControlMessageProcessor(IConfigurationReader configurationReader,
            IControlMessageHandler  messageHandler, 
            IConfigurationHandler configurationHandler,
            IErrorHandler errorHandler)
        {
            _configurationReader = configurationReader ?? throw new ArgumentNullException(nameof(configurationReader));
            _messageHandler = messageHandler ?? throw new ArgumentNullException(nameof(messageHandler));
            _configurationHandler = configurationHandler ?? throw new ArgumentNullException(nameof(configurationHandler));
            _errorHandler = errorHandler ?? throw new ArgumentNullException(nameof(errorHandler));
        }

        public void ProcessIncomingMessage(BinaryReader inputReader)
        {
            byte messageType = inputReader.ReadByte();

            switch (messageType)
            {
                case MessageTypes.Start:
                    _messageHandler.OnStart();
                    break;
                case MessageTypes.Stop:
                    _messageHandler.OnStop();
                    break;
                case MessageTypes.Pause:
                    _messageHandler.OnPause();
                    break;
                case MessageTypes.Unpause:
                    _messageHandler.OnUnpause();
                    break;
                case MessageTypes.Suspend:
                    _messageHandler.OnSuspend();
                    break;
                case MessageTypes.Unsuspend:
                    _messageHandler.OnUnsuspend();
                    break;
                case MessageTypes.Configuration:
                    _configurationHandler.OnConfig(_configurationReader.ReadConfiguration(inputReader));
                    break;
                case MessageTypes.Error:
                    _messageHandler.OnError(inputReader.ReadUtfBigEndian());
                    break;
                default:
                    _errorHandler.HandleError("Unrecognized control message in ProcessIncomingMessage.", null);
                    break;
            }
        }
    }
}

/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codedx.codepulse.agent.control;

import java.io.DataInputStream;
import java.io.IOException;

import com.codedx.codepulse.agent.errors.ErrorHandler;
import com.codedx.codepulse.agent.common.message.MessageConstantsV1;

/**
 * Processes incoming control messages (protocol version 1)
 * @author RobertF
 */
public class ControlMessageProcessorV1 implements ControlMessageProcessor
{
	private final ConfigurationReader configReader;
	private final ControlMessageHandler handler;
	private final ConfigurationHandler configHandler;

	public ControlMessageProcessorV1(ConfigurationReader configReader,
			ControlMessageHandler handler, ConfigurationHandler configHandler)
	{
		this.configReader = configReader;
		this.handler = handler;
		this.configHandler = configHandler;
	}

	@Override
	public void processIncomingMessage(DataInputStream stream) throws IOException
	{
		byte messageType = stream.readByte();

		switch (messageType)
		{
		case MessageConstantsV1.MsgStart:
			handler.onStart();
			break;
		case MessageConstantsV1.MsgStop:
			handler.onStop();
			break;
		case MessageConstantsV1.MsgPause:
			handler.onPause();
			break;
		case MessageConstantsV1.MsgUnpause:
			handler.onUnpause();
			break;
		case MessageConstantsV1.MsgSuspend:
			handler.onSuspend();
			break;
		case MessageConstantsV1.MsgUnsuspend:
			handler.onUnsuspend();
			break;
		case MessageConstantsV1.MsgConfiguration:
			configHandler.onConfig(configReader.readConfiguration(stream));
			break;
		case MessageConstantsV1.MsgError:
			handler.onError(stream.readUTF());
			break;
		default:
			ErrorHandler.handleError("unrecognized control message in processIncomingMessage");
		}
	}
}

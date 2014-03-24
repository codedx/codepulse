/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
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

package com.secdec.bytefrog.agent.init;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.secdec.bytefrog.agent.control.ConfigurationReader;
import com.secdec.bytefrog.agent.errors.ErrorHandler;
import com.secdec.bytefrog.common.config.RuntimeAgentConfigurationV1;
import com.secdec.bytefrog.common.connect.Connection;
import com.secdec.bytefrog.common.message.MessageConstantsV1;
import com.secdec.bytefrog.common.message.MessageProtocol;

/**
 * Implements control connection handshake for protocol version 1.
 * @author RobertF
 */
public class ControlConnectionHandshakeV1 implements ControlConnectionHandshake
{
	private final MessageProtocol protocol;
	private final ConfigurationReader configReader;

	public ControlConnectionHandshakeV1(MessageProtocol protocol, ConfigurationReader configReader)
	{
		this.protocol = protocol;
		this.configReader = configReader;
	}

	@Override
	public RuntimeAgentConfigurationV1 performHandshake(Connection connection) throws IOException
	{
		DataOutputStream outStream = connection.output();
		DataInputStream inStream = connection.input();

		// say hello!
		protocol.writeHello(outStream);
		outStream.flush();

		byte reply = inStream.readByte();
		switch (reply)
		{
		case MessageConstantsV1.MsgConfiguration:
			return configReader.readConfiguration(inStream);
		case MessageConstantsV1.MsgError:
			ErrorHandler.handleError(String.format("received error from handshake: %s",
					inStream.readUTF()));
			return null;
		default:
			ErrorHandler.handleError("protocol error: invalid or unexpected control message");
			return null;
		}
	}
}

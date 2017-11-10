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

package com.codedx.codepulse.agent.init;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.codedx.codepulse.agent.errors.ErrorHandler;
import com.codedx.codepulse.agent.common.connect.Connection;
import com.codedx.codepulse.agent.common.message.MessageConstantsV1;
import com.codedx.codepulse.agent.common.message.MessageProtocol;

public class DataConnectionHandshakeV1 implements DataConnectionHandshake
{
	private final MessageProtocol protocol;

	public DataConnectionHandshakeV1(MessageProtocol protocol)
	{
		this.protocol = protocol;
	}

	@Override
	public boolean performHandshake(byte runId, Connection connection) throws IOException
	{
		boolean success = false;
		DataOutputStream out = connection.output();
		DataInputStream in = connection.input();

		protocol.writeDataHello(out, runId);
		out.flush();

		byte reply = in.readByte();

		switch (reply)
		{
		// expect a DataHelloReply in response to the DataHello we sent
		case MessageConstantsV1.MsgDataHelloReply:
			success = true;
			break;
		// report any "error" response
		case MessageConstantsV1.MsgError:
			String err = in.readUTF();
			ErrorHandler.handleError("received error during data handshake: " + err);
			break;
		// anything else is a protocol error
		default:
			ErrorHandler.handleError("protocol error: invalid or unexpected control message");
		}

		return success;
	}

}

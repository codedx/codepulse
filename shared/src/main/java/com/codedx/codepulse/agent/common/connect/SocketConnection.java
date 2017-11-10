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

package com.codedx.codepulse.agent.common.connect;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class SocketConnection implements Connection
{
	private final Socket underlying;
	private final DataInputStream input;
	private final DataOutputStream output;

	public SocketConnection(Socket underlying) throws IOException
	{
		this(underlying, true, true);
	}

	public SocketConnection(Socket underlying, boolean bufferIn, boolean bufferOut)
			throws IOException
	{
		this.underlying = underlying;

		InputStream in = underlying.getInputStream();
		if (bufferIn)
			in = new BufferedInputStream(in);
		this.input = new DataInputStream(in);

		OutputStream out = underlying.getOutputStream();
		if (bufferOut)
			out = new BufferedOutputStream(out);
		this.output = new DataOutputStream(out);
	}

	@Override
	public void close() throws IOException
	{
		input.close();
		output.close();
		underlying.close();
	}

	/**
	 * Why not "getInput"? Because Java sucks and Scala is great.
	 */
	@Override
	public DataInputStream input()
	{
		return this.input;
	}

	/**
	 * Why not "getOutput"? Because Java sucks and Scala is great.
	 */
	@Override
	public DataOutputStream output()
	{
		return this.output;
	}

	public Socket socket()
	{
		return this.underlying;
	}
}

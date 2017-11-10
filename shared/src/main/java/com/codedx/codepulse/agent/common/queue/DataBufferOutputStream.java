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

package com.codedx.codepulse.agent.common.queue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream decorator for ByteArrayOutputStream. It provides all of the
 * conveniences of a DataOutputStream, along with the ability to reset the
 * buffer and dump it to another stream.
 *
 * @author dylanh
 */
public class DataBufferOutputStream extends DataOutputStream
{

	private final ByteArrayOutputStream underlying;

	/**
	 * Initialize this DataBuffer with an underlying ByteArrayOutputStream.
	 *
	 * @param underlying
	 */
	public DataBufferOutputStream(ByteArrayOutputStream underlying)
	{
		super(underlying);
		this.underlying = underlying;
	}

	public DataBufferOutputStream(int capacity)
	{
		this(new ByteArrayOutputStream(capacity));
	}

	/**
	 * Delegates to <code>underlying.writeTo(...)</code>
	 *
	 * @param out
	 * @throws IOException
	 */
	public void writeTo(OutputStream out) throws IOException
	{
		underlying.writeTo(out);
	}

	/**
	 * Delegates to <code>underlying.reset()</code>
	 */
	public void reset()
	{
		underlying.reset();
		written = 0;
	}

	public byte[] toByteArray()
	{
		return underlying.toByteArray();
	}
}

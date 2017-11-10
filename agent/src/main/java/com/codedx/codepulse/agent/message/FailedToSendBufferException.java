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

package com.codedx.codepulse.agent.message;

/**
 * Exception thrown when the MessageFactory fails to send a data buffer.
 *
 * @author dylanh
 */
public class FailedToSendBufferException extends Exception
{
	private static final long serialVersionUID = -96435329391357270L;

	public FailedToSendBufferException()
	{
		super();
	}

	public FailedToSendBufferException(String message)
	{
		super(message);
	}

	public FailedToSendBufferException(Throwable reason)
	{
		super(reason);
	}

	public FailedToSendBufferException(String message, Throwable reason)
	{
		super(message, reason);
	}
}
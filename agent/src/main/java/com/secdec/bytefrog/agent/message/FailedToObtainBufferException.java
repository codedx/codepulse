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

package com.secdec.bytefrog.agent.message;

/**
 * Exception thrown when the MessageFactory fails to obtain a data buffer.
 * 
 * @author dylanh
 */
public class FailedToObtainBufferException extends Exception
{
	private static final long serialVersionUID = -7150595450307222564L;

	public FailedToObtainBufferException()
	{
		super();
	}

	public FailedToObtainBufferException(String message)
	{
		super(message);
	}

	public FailedToObtainBufferException(Throwable reason)
	{
		super(reason);
	}

	public FailedToObtainBufferException(String message, Throwable reason)
	{
		super(message, reason);
	}
}
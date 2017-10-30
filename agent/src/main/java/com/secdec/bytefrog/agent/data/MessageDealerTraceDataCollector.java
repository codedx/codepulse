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

package com.secdec.bytefrog.agent.data;

import com.secdec.bytefrog.agent.TraceDataCollector;
import com.secdec.bytefrog.agent.errors.ErrorHandler;
import com.secdec.bytefrog.agent.message.MessageDealer;

/**
 * Concrete implementation of TraceDataCollector that passes data to a
 * MessageDealer.
 * @author RobertF
 */
public class MessageDealerTraceDataCollector implements TraceDataCollector
{
	private final MessageDealer messageDealer;

	public MessageDealerTraceDataCollector(MessageDealer messageDealer)
	{
		this.messageDealer = messageDealer;
	}

	@Override
	public void methodEntry(String methodSig)
	{
		try
		{
			messageDealer.sendMethodEntry(methodSig);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending method entry", e);
		}
	}

	@Override
	public void methodExit(String methodSig, int sourceLine)
	{
		try
		{
			messageDealer.sendMethodExit(methodSig, sourceLine);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending method exit", e);
		}
	}

	@Override
	public void exception(String exception, String methodSig, int sourceLine)
	{
		try
		{
			messageDealer.sendException(exception, methodSig, sourceLine);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending exception", e);
		}
	}

	@Override
	public void bubbleException(String exception, String methodSig)
	{
		try
		{
			messageDealer.sendExceptionBubble(exception, methodSig);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending exception bubble", e);
		}
	}

	@Override
	public void marker(String key, String value)
	{
		try
		{
			messageDealer.sendMarker(key, value);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending marker", e);
		}

	}
}

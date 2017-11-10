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

package com.codedx.codepulse.agent.data;

import com.codedx.codepulse.agent.errors.ErrorHandler;
import com.codedx.codepulse.agent.message.MessageDealer;
import com.codedx.codepulse.agent.trace.TraceDataCollector;

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
	public void methodEntry(int methodId)
	{
		try
		{
			messageDealer.sendMethodEntry(methodId);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending method entry", e);
		}
	}

	@Override
	public void methodExit(int methodId, int sourceLine)
	{
		try
		{
			messageDealer.sendMethodExit(methodId, sourceLine);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending method exit", e);
		}
	}

	@Override
	public void exception(String exception, int methodId, int sourceLine)
	{
		try
		{
			messageDealer.sendException(exception, methodId, sourceLine);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending exception", e);
		}
	}

	@Override
	public void bubbleException(String exception, int methodId)
	{
		try
		{
			messageDealer.sendExceptionBubble(exception, methodId);
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

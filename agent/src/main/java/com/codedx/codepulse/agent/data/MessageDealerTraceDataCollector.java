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
	public void methodExit(int methodId, boolean exThrown)
	{
		try
		{
			messageDealer.sendMethodExit(methodId, exThrown);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error sending method exit", e);
		}
	}

	@Override
	public void recordLineLevelTrace(int methodId, int startLine, int endLine, java.util.BitSet lineMap)
	{
		try
		{
			messageDealer.recordLineLevelTrace(methodId, startLine, endLine, lineMap);
		}
		catch (Exception e)
		{
			ErrorHandler.handleError("error recording line level trace", e);
		}
	}
}

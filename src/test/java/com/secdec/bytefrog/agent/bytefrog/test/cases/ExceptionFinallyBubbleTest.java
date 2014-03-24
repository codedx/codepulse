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

package com.secdec.bytefrog.agent.bytefrog.test.cases;

import java.io.IOException;

/**
 * Simple bytefrog test that throws, bubbles, and catches an exception. The
 * method that throws has a finally block, which will cause a double-throw
 * (since it will rethrow at end of that block).
 * 
 * @author RobertF
 */
public class ExceptionFinallyBubbleTest
{
	public static void main(String[] arguments)
	{
		try
		{
			thrower();
		}
		catch (IOException e)
		{
		}
	}

	@SuppressWarnings("unused")
	static void thrower() throws IOException
	{
		try
		{
			throw new IOException();
		}
		finally
		{
			int i = 1 + 1;
		}
	}
}

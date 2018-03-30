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

package com.secdec.bytefrog.agent.bytefrog.test.cases;

import java.io.IOException;

/**
 * Simple bytefrog contains a constructor that throws.
 *
 * @author RobertF
 */
public class ConstructorThrowTest
{
	public static void main(String[] arguments)
	{
		try
		{
			new ConstructorThrowTest();
		}
		catch (IOException e)
		{
		}
	}

	private ConstructorThrowTest() throws IOException
	{
		throw new IOException();
	}
}

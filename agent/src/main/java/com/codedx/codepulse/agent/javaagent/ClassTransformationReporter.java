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

package com.codedx.codepulse.agent.javaagent;

import java.io.IOException;

import com.codedx.codepulse.agent.control.Controller;
import com.codedx.codepulse.agent.errors.ErrorHandler;
import com.codedx.codepulse.agent.trace.ClassTransformationListener;

/**
 * A ClassTransformationListener implementation that will handle classes being
 * transformed and ignored by sending ClassTransformed and ClassIgnored messages
 * through a control connection, respectively.
 * @author DylanH
 */
public class ClassTransformationReporter extends ClassTransformationListener
{

	private final Controller controller;

	public ClassTransformationReporter(Controller controller)
	{
		this.controller = controller;
	}

	@Override
	public void classTransformed(String className, ClassLoader loader)
	{
		try
		{
			if (controller.isRunning())
				controller.sendClassTransformed(className);
		}
		catch (IOException e)
		{
			ErrorHandler.handleError("Failed to send ClassTransformed message", e);
		}
	}

	@Override
	public void classTransformFailed(String className, ClassLoader loader, Throwable cause,
			String message)
	{
		try
		{
			if (controller.isRunning())
				controller.sendClassTransformFailed(className);
		}
		catch (IOException e)
		{
			ErrorHandler.handleError("Failed to send ClassTransformed message", e);
		}
	}

	@Override
	public void classIgnored(String className, ClassLoader loader)
	{
		try
		{
			if (controller.isRunning())
				controller.sendClassIgnored(className);
		}
		catch (IOException e)
		{
			ErrorHandler.handleError("Failed to send ClassIgnored message", e);
		}
	}

}

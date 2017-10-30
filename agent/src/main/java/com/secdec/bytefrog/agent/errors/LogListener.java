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

package com.secdec.bytefrog.agent.errors;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LogListener implements ErrorListener
{
	private final Logger logger;
	private final FileHandler fileHandler;

	public LogListener(String filename) throws SecurityException, IOException
	{
		logger = Logger.getAnonymousLogger();
		logger.setLevel(Level.SEVERE);

		if (filename != null)
		{
			fileHandler = new FileHandler(filename);
			fileHandler.setFormatter(new SimpleFormatter());
			logger.addHandler(fileHandler);
		}
		else
			fileHandler = null;
	}

	public void close()
	{
		if (fileHandler != null)
		{
			logger.removeHandler(fileHandler);
			fileHandler.close();
		}
	}

	@Override
	public void onErrorReported(String errorMessage, Exception exception)
	{
		try
		{
			logger.log(Level.SEVERE, errorMessage, exception);
		}
		catch (Exception e)
		{
			// swallow any exceptions writing to the log file, so they don't
			// bubble up where they shouldn't
		}
	}
}

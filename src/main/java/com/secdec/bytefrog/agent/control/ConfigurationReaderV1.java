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

package com.secdec.bytefrog.agent.control;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import com.secdec.bytefrog.agent.errors.ErrorHandler;
import com.secdec.bytefrog.common.config.RuntimeAgentConfigurationV1;

/**
 * Reads incoming configuration packets (version 1)
 * 
 * @author robertf
 */
public class ConfigurationReaderV1 implements ConfigurationReader
{
	@Override
	public RuntimeAgentConfigurationV1 readConfiguration(DataInputStream stream) throws IOException
	{
		// read configuration length
		int configLen = stream.readInt();

		// read configuration data
		byte[] configBuffer = new byte[configLen];
		stream.read(configBuffer, 0, configLen);

		ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(configBuffer));
		try
		{
			return (RuntimeAgentConfigurationV1) in.readObject();
		}
		catch (ClassNotFoundException ex)
		{
			ErrorHandler.handleError("error processing configuration", ex);
			return null;
		}
	}
}

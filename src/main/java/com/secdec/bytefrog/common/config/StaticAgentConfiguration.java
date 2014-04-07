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

package com.secdec.bytefrog.common.config;

import java.util.Properties;

/**
 * Static configuration for Agent.
 * 
 * @author RobertF
 */
public class StaticAgentConfiguration
{
	public static int DefaultConnectTimeout = 30;

	/**
	 * Parses an options string, expected to be in the form
	 * <code>host:port;key=value;key2=value2;...</code> or
	 * <code>host:port;logfile</code> (provided for backward compatibility).
	 * 
	 * Recognized configuration keys are log (for the agent log file) and
	 * connectTimeout (to control the timeout when attempting to connect to HQ).
	 * 
	 * @param options
	 * @return A new configuration instance on success. <code>null</code> on
	 *         failure.
	 */
	public static StaticAgentConfiguration parseOptionString(String options)
	{
		String[] optionParts = options.split(";");
		Properties props = new Properties();

		String hqEndpoint = optionParts[0];
		String[] hqEndpointParts = hqEndpoint.split(":");
		if (hqEndpointParts.length < 2)
			return null;

		String hqHost = hqEndpointParts[0];
		int hqPort;
		try
		{
			hqPort = Integer.parseInt(hqEndpointParts[1]);
		}
		catch (NumberFormatException e)
		{
			return null;
		}

		if (optionParts.length == 2 && !optionParts[1].contains("="))
		{
			// "legacy" mode, second value is just the filename
			props.setProperty("log", optionParts[1]);
		}
		else
		{
			// key/value pairs
			for (int i = 1; i < optionParts.length; i++)
			{
				String[] kvp = optionParts[i].split("=", 2);
				if (kvp.length == 2)
					props.setProperty(kvp[0], kvp[1]);
			}
		}

		String logFilename = props.getProperty("log");
		int connectTimeout;
		try
		{
			connectTimeout = Integer.parseInt(props.getProperty("connectTimeout",
					String.valueOf(DefaultConnectTimeout)));
		}
		catch (NumberFormatException e)
		{
			return null;
		}

		return new StaticAgentConfiguration(hqHost, hqPort, logFilename, connectTimeout);
	}

	private final int hqPort;
	private final String hqHost;
	private final String logFilename;
	private final int connectTimeout;

	public StaticAgentConfiguration(String hqHost, int hqPort)
	{
		this(hqHost, hqPort, null);
	}

	public StaticAgentConfiguration(String hqHost, int hqPort, String logFilename)
	{
		this(hqHost, hqPort, logFilename, DefaultConnectTimeout);
	}

	public StaticAgentConfiguration(String hqHost, int hqPort, String logFilename,
			int connectTimeout)
	{
		this.hqHost = hqHost;
		this.hqPort = hqPort;
		this.logFilename = logFilename;
		this.connectTimeout = connectTimeout;
	}

	public String toOptionString()
	{
		Properties props = new Properties();
		if (logFilename != null)
			props.setProperty("log", logFilename);
		props.setProperty("connectTimeout", String.valueOf(connectTimeout));

		StringBuilder sb = new StringBuilder();
		sb.append(hqHost);
		sb.append(':');
		sb.append(hqPort);

		for (String key : props.stringPropertyNames())
		{
			sb.append(';');
			sb.append(key);
			sb.append('=');
			sb.append(props.getProperty(key));
		}

		return sb.toString();
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder("StaticConfiguration(");
		sb.append("logFilename=").append(logFilename);
		sb.append(" )");
		return sb.toString();
	}

	public String getHqHost()
	{
		return hqHost;
	}

	public int getHqPort()
	{
		return hqPort;
	}

	public String getLogFilename()
	{
		return logFilename;
	}

	public int getConnectTimeout()
	{
		return connectTimeout;
	}
}

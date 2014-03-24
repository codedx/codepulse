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

/**
 * Static configuration for Agent.
 * @author RobertF
 */
public class StaticAgentConfiguration
{
	/**
	 * Parses an options string, expected to be in the form
	 * <code>host:port;logfile</code>.
	 * @param options
	 * @return A new configuration instance on success. <code>null</code> on
	 *         failure.
	 */
	public static StaticAgentConfiguration parseOptionString(String options)
	{
		String[] optionParts = options.split(";", 2);

		String logFilename = optionParts.length > 1 ? optionParts[1] : null;

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

		return new StaticAgentConfiguration(hqHost, hqPort, logFilename);
	}

	private final int hqPort;
	private final String hqHost;
	private final String logFilename;

	public StaticAgentConfiguration(String hqHost, int hqPort)
	{
		this(hqHost, hqPort, null);
	}

	public StaticAgentConfiguration(String hqHost, int hqPort, String logFilename)
	{
		this.hqHost = hqHost;
		this.hqPort = hqPort;
		this.logFilename = logFilename;
	}

	public String toOptionString()
	{
		return hqHost + ":" + hqPort + (logFilename != null ? ";" + logFilename : "");
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
}

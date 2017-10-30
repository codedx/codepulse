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

package com.codedx.codepulse.agent.common.util;

import java.util.Iterator;

public class StringUtil
{
	private StringUtil()
	{
	}

	/**
	 * Just like mkString in Scala.
	 *
	 * @param items
	 * @param start
	 * @param between
	 * @param end
	 * @return
	 */
	public static String mkString(Iterable<?> items, String start, String between, String end)
	{
		if (start == null)
			start = "";
		if (between == null)
			between = "";
		if (end == null)
			end = "";
		StringBuilder sb = new StringBuilder(start);
		Iterator<?> itr = items.iterator();
		boolean isFirst = true;
		while (itr.hasNext())
		{
			if (!isFirst)
			{
				sb.append(between);
			}
			else
			{
				isFirst = false;
			}
			sb.append(itr.next());
		}
		sb.append(end);
		return sb.toString();
	}

	/**
	 * Just like mkString in Scala.
	 *
	 * @param items
	 * @param between
	 * @return
	 */
	public static String mkString(Iterable<?> items, String between)
	{
		return mkString(items, null, between, null);
	}

	public static String mkString(byte[] items, String start, String between, String end)
	{
		start = (start == null) ? "" : start;
		between = (between == null) ? "" : between;
		end = (end == null) ? "" : end;

		StringBuilder sb = new StringBuilder(start);
		for (int i = 0; i < items.length; i++)
		{
			if (i > 0)
			{
				sb.append(between);
			}
			int item = items[i] & 0xFF;
			if (Character.isLetter(item))
			{
				sb.append("'" + (char) item + "'");
			}
			else
			{
				sb.append("x" + Integer.toHexString(item).toUpperCase());
			}
		}
		sb.append(end);

		return sb.toString();
	}
}

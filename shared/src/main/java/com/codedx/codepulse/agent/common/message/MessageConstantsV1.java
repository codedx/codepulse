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

package com.codedx.codepulse.agent.common.message;

/**
 * Defines the byte constants for message headers for message protocol version
 * 1.
 *
 * @author dylanh
 */
public class MessageConstantsV1
{
	private MessageConstantsV1()
	{
		// This class is not meant to be instantiated
	}

	public static final byte MsgHello = 0;
	public static final byte MsgConfiguration = 1;
	public static final byte MsgStart = 2;
	public static final byte MsgStop = 3;
	public static final byte MsgPause = 4;
	public static final byte MsgUnpause = 5;
	public static final byte MsgSuspend = 6;
	public static final byte MsgUnsuspend = 7;
	public static final byte MsgHeartbeat = 8;
	public static final byte MsgDataBreak = 9;
	public static final byte MsgMapThreadName = 10;
	public static final byte MsgMapMethodSignature = 11;
	public static final byte MsgMapException = 12;
	public static final byte MsgMethodEntry = 20;
	public static final byte MsgMethodExit = 21;
	public static final byte MsgException = 22;
	public static final byte MsgExceptionBubble = 23;
	public static final byte MsgDataHello = 30;
	public static final byte MsgDataHelloReply = 31;
	public static final byte MsgClassTransformed = 40;
	public static final byte MsgClassIgnored = 41;
	public static final byte MsgClassTransformFailed = 42;
	public static final byte MsgMarker = 50;
	public static final byte MsgError = 99;
}

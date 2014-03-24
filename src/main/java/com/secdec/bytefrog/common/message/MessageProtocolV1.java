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

package com.secdec.bytefrog.common.message;

import java.io.DataOutputStream;
import java.io.IOException;

public class MessageProtocolV1 implements MessageProtocol
{

	@Override
	public byte protocolVersion()
	{
		return 1;
	}

	@Override
	public void writeHello(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgHello);
		out.writeByte(protocolVersion());
	}

	@Override
	public void writeDataHello(DataOutputStream out, byte runId) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgDataHello);
		out.writeByte(runId);
	}

	@Override
	public void writeError(DataOutputStream out, String error) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgError);
		out.writeUTF(error);
	}

	@Override
	public void writeConfiguration(DataOutputStream out, byte[] configBytes) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgConfiguration);
		out.writeInt(configBytes.length);
		out.write(configBytes);
	}

	@Override
	public void writeDataHelloReply(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgDataHelloReply);
	}

	@Override
	public void writeStart(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgStart);
	}

	@Override
	public void writeStop(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgStop);
	}

	@Override
	public void writePause(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgPause);
	}

	@Override
	public void writeUnpause(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgUnpause);
	}

	@Override
	public void writeSuspend(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgSuspend);
	}

	@Override
	public void writeUnsuspend(DataOutputStream out) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgUnsuspend);
	}

	@Override
	public void writeHeartbeat(DataOutputStream out, AgentOperationMode mode, int sendBufferSize)
			throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgHeartbeat);
		switch (mode)
		{
		case Initializing:
			out.writeByte(73);
			break;
		case Paused:
			out.writeByte(80);
			break;
		case Suspended:
			out.writeByte(83);
			break;
		case Tracing:
			out.writeByte(84);
			break;
		case Shutdown:
			out.writeByte(88);
			break;
		default:
			throw new IllegalStateException(
					"Incomplete match on AgentOperationMode. This should never happen");
		}
		out.writeShort(sendBufferSize);
	}

	@Override
	public void writeDataBreak(DataOutputStream out, int sequenceId) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgDataBreak);
		out.writeInt(sequenceId);
	}

	@Override
	public void writeClassTransformed(DataOutputStream out, String className) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgClassTransformed);
		out.writeUTF(className);
	}

	@Override
	public void writeClassTransformFailed(DataOutputStream out, String className)
			throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgClassTransformFailed);
		out.writeUTF(className);
	}

	@Override
	public void writeClassIgnored(DataOutputStream out, String className) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgClassIgnored);
		out.writeUTF(className);
	}

	@Override
	public void writeMapThreadName(DataOutputStream out, int threadId, int relTime,
			String threadName) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgMapThreadName);
		out.writeShort(threadId);
		out.writeInt(relTime);
		out.writeUTF(threadName);
	}

	@Override
	public void writeMapMethodSignature(DataOutputStream out, int sigId, String signature)
			throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgMapMethodSignature);
		out.writeInt(sigId);
		out.writeUTF(signature);
	}

	@Override
	public void writeMapException(DataOutputStream out, int excId, String exception)
			throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgMapException);
		out.writeInt(excId);
		out.writeUTF(exception);
	}

	@Override
	public void writeMethodEntry(DataOutputStream out, int relTime, int seq, int sigId, int threadId)
			throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgMethodEntry);
		out.writeInt(relTime);
		out.writeInt(seq);
		out.writeInt(sigId);
		out.writeShort(threadId);
	}

	@Override
	public void writeMethodExit(DataOutputStream out, int relTime, int seq, int sigId, int lineNum,
			int threadId) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgMethodExit);
		out.writeInt(relTime);
		out.writeInt(seq);
		out.writeInt(sigId);
		out.writeShort(lineNum);
		out.writeShort(threadId);
	}

	@Override
	public void writeException(DataOutputStream out, int relTime, int seq, int methodSigId,
			int excId, int lineNum, int threadId) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgException);
		out.writeInt(relTime);
		out.writeInt(seq);
		out.writeInt(methodSigId);
		out.writeInt(excId);
		out.writeShort(lineNum);
		out.writeShort(threadId);
	}

	@Override
	public void writeExceptionBubble(DataOutputStream out, int relTime, int seq, int sigId,
			int excId, int threadId) throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgExceptionBubble);
		out.writeInt(relTime);
		out.writeInt(seq);
		out.writeInt(sigId);
		out.writeInt(excId);
		out.writeShort(threadId);
	}

	@Override
	public void writeMarker(DataOutputStream out, String key, String value, int relTime, int seq)
			throws IOException
	{
		out.writeByte(MessageConstantsV1.MsgMarker);
		out.writeInt(relTime);
		out.writeInt(seq);
		out.writeUTF(key);
		out.writeUTF(value);
	}
}

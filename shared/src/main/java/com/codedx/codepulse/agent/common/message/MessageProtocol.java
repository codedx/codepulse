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

package com.codedx.codepulse.agent.common.message;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Defines the behavior of an object that can write messages to a
 * {@link DataOutputStream} according to the Message Protocol Specification.
 *
 * @author dylanh
 *
 */
public interface MessageProtocol
{
	public byte protocolVersion();

	public void writeHello(DataOutputStream out) throws IOException;

	public void writeDataHello(DataOutputStream out, byte runId) throws IOException;

	public void writeError(DataOutputStream out, String error) throws IOException;

	public void writeConfiguration(DataOutputStream out, byte[] configBytes) throws IOException;

	public void writeDataHelloReply(DataOutputStream out) throws IOException;

	public void writeStart(DataOutputStream out) throws IOException;

	public void writeStop(DataOutputStream out) throws IOException;

	public void writePause(DataOutputStream out) throws IOException;

	public void writeUnpause(DataOutputStream out) throws IOException;

	public void writeSuspend(DataOutputStream out) throws IOException;

	public void writeUnsuspend(DataOutputStream out) throws IOException;

	public void writeHeartbeat(DataOutputStream out, AgentOperationMode mode, int sendBufferSize)
			throws IOException;

	public void writeDataBreak(DataOutputStream out, int sequenceId) throws IOException;

	public void writeClassTransformed(DataOutputStream out, String className) throws IOException;

	public void writeClassTransformFailed(DataOutputStream out, String className)
			throws IOException;

	public void writeClassIgnored(DataOutputStream out, String className) throws IOException;

	public void writeMapThreadName(DataOutputStream out, int threadId, int relTime,
			String threadName) throws IOException;

	public void writeMapMethodSignature(DataOutputStream out, int sigId, String signature)
			throws IOException;

	public void writeMapException(DataOutputStream out, int excId, String exception)
			throws IOException;

	public void writeMethodEntry(DataOutputStream out, int relTime, int seq, int sigId, int threadId)
			throws IOException;

	public void writeMethodExit(DataOutputStream out, int relTime, int seq, int sigId, int lineNum,
			int threadId) throws IOException;

	public void writeException(DataOutputStream out, int relTime, int seq, int sigId, int excId,
			int lineNum, int threadId) throws IOException;

	public void writeExceptionBubble(DataOutputStream out, int relTime, int seq, int sigId,
			int excId, int threadId) throws IOException;

	public void writeMarker(DataOutputStream out, String key, String value, int relTime, int seq)
			throws IOException;

	public void writeConfiguration(DataOutputStream out, String configJson) throws IOException, NotSupportedException;
}

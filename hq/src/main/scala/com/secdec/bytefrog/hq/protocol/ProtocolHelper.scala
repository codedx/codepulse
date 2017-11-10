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

package com.codedx.codepulse.hq.protocol

import com.codedx.codepulse.agent.common.message.MessageProtocol

trait ProtocolHelper {

	def latestProtocolVersion: Int

	def getMessageProtocol(protocolVersion: Int): Option[MessageProtocol]
	def getControlMessageSender(protocolVersion: Int): Option[ControlMessageSender]
	def getControlMessageReader(protocolVersion: Int): Option[ControlMessageReader]
	def getDataEventReader(version: Int): Option[DataEventReader]
	def getDataMessageReader(version: Int): Option[DataMessageReader]
	def getDataMessageParser(version: Int): Option[DataMessageParser]

	def latestProtocol = getMessageProtocol(latestProtocolVersion) getOrElse {
		throw new NotImplementedError("No 'latest' protocol. This is an implementation error.")
	}

	def latestControlMessageSender = getControlMessageSender(latestProtocolVersion) getOrElse {
		throw new NotImplementedError("No 'latest' ControlMessageSender. This is an implementation error.")
	}

}
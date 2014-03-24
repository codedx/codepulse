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

package com.secdec.bytefrog.hq.protocol

import com.secdec.bytefrog.common.message.MessageProtocol
import com.secdec.bytefrog.common.message.MessageProtocolV1

object DefaultProtocolHelper extends ProtocolHelper {

	def latestProtocolVersion = 1

	/** Returns a `MessageProtocol` instance associated with the given `version`, as
	  * an option.
	  *
	  * @return `Some` with the `MessageProtocol` instance, if one exists, or `None` if
	  * no such class exists.
	  */
	def getMessageProtocol(version: Int): Option[MessageProtocol] = version match {
		case 1 => Some(new MessageProtocolV1)
		case _ => None
	}

	/** Returns a `ControlMessageSneder` instance associated with the given `version`,
	  * as an option.
	  *
	  * @return `Some` with the appropriate `ControlMessageSender` if it exists, or
	  * `None` if no such class exists.
	  */
	def getControlMessageSender(version: Int): Option[ControlMessageSender] = version match {
		case 1 => Some(ControlMessageSenderV1)
		case _ => None
	}

	def getControlMessageReader(version: Int): Option[ControlMessageReader] = version match {
		case 1 => Some(ControlMessageReaderV1)
		case _ => None
	}

	def getDataEventReader(version: Int): Option[DataEventReader] = version match {
		//event reader V1 isn't thread safe, so return a new instance each time
		case 1 => Some(new DataEventReaderV1)
		case _ => None
	}

	def getDataMessageReader(version: Int): Option[DataMessageReader] = version match {
		case 1 => Some(DataMessageReaderV1)
		case _ => None
	}

	def getDataMessageParser(version: Int): Option[DataMessageParser] = version match {
		case 1 => Some(DataMessageParserV1)
		case _ => None
	}
}
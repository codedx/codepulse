/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */

package com.codedx.codepulse.agent.protocol;

import com.codedx.codepulse.agent.common.message.MessageProtocolV4;
import com.codedx.codepulse.agent.control.ConfigurationReaderV2;
import com.codedx.codepulse.agent.init.ControlConnectionHandshakeV1;
import com.codedx.codepulse.agent.init.DataConnectionHandshakeV1;

/**
 * ProtocolVersion implementation for version 4.
 * @author ssalas
 */
public class ProtocolVersion4 extends ProtocolVersionBase
{
	public ProtocolVersion4()
	{
		messageProtocol = new MessageProtocolV4();
		configurationReader = new ConfigurationReaderV2();
		controlConnectionHandshake = new ControlConnectionHandshakeV1(messageProtocol, configurationReader);
		dataConnectionHandshake = new DataConnectionHandshakeV1(messageProtocol);
	}
}

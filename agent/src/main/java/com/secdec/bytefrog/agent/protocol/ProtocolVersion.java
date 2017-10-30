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

package com.secdec.bytefrog.agent.protocol;

import com.secdec.bytefrog.agent.control.ConfigurationHandler;
import com.secdec.bytefrog.agent.control.ConfigurationReader;
import com.secdec.bytefrog.agent.control.ControlMessageHandler;
import com.secdec.bytefrog.agent.control.ControlMessageProcessor;
import com.secdec.bytefrog.agent.init.ControlConnectionHandshake;
import com.secdec.bytefrog.agent.init.DataConnectionHandshake;
import com.secdec.bytefrog.common.message.MessageProtocol;

/**
 * Groups together protocol related classes.
 * @author RobertF
 */
public interface ProtocolVersion
{
	MessageProtocol getMessageProtocol();

	ConfigurationReader getConfigurationReader();

	ControlConnectionHandshake getControlConnectionHandshake();

	DataConnectionHandshake getDataConnectionHandshake();

	ControlMessageProcessor getControlMessageProcessor(ControlMessageHandler handler,
			ConfigurationHandler configHandler);
}

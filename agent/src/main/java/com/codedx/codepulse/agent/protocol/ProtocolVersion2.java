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

package com.codedx.codepulse.agent.protocol;

import com.codedx.codepulse.agent.common.message.MessageProtocolV2;
import com.codedx.codepulse.agent.control.*;
import com.codedx.codepulse.agent.init.ControlConnectionHandshake;
import com.codedx.codepulse.agent.init.ControlConnectionHandshakeV1;
import com.codedx.codepulse.agent.init.DataConnectionHandshake;
import com.codedx.codepulse.agent.init.DataConnectionHandshakeV1;
import com.codedx.codepulse.agent.common.message.MessageProtocol;
import com.codedx.codepulse.agent.common.message.MessageProtocolV1;

/**
 * ProtocolVersion implementation for version 2.
 * @author ssalas
 */
public class ProtocolVersion2 extends ProtocolVersionBase
{
    public ProtocolVersion2()
    {
        messageProtocol = new MessageProtocolV2();
        configurationReader = new ConfigurationReaderV2();
        controlConnectionHandshake = new ControlConnectionHandshakeV1(messageProtocol, configurationReader);
        dataConnectionHandshake = new DataConnectionHandshakeV1(messageProtocol);
    }
}

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

package com.codedx.codepulse.agent.control;

import com.codedx.codepulse.agent.common.config.RuntimeAgentConfigurationV1;

import javax.json.bind.Jsonb;
import javax.json.bind.spi.JsonbProvider;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Reads incoming configuration packets (version 2)
 *
 * @author ssalas
 */
public class ConfigurationReaderV2 implements ConfigurationReader
{
    @Override
    public RuntimeAgentConfigurationV1 readConfiguration(DataInputStream stream) throws IOException
    {
        Jsonb jsonb = JsonbProvider.provider().create().build();
        return jsonb.fromJson(stream.readUTF(), RuntimeAgentConfigurationV1.class);
    }
}
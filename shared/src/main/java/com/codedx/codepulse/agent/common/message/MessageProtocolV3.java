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

package com.codedx.codepulse.agent.common.message;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.codedx.codepulse.agent.common.message.MessageConstantsV3.MsgMethodVisit;

public class MessageProtocolV3 extends MessageProtocolV2 {

    @Override
    public byte protocolVersion()
    {
        return 3;
    }

    public void writeMapSourceLocation(DataOutputStream out, int sourceLocationId, int sigId, int startLine, int endLine, short startCharacter, short endCharacter)
            throws IOException
    {
        out.writeByte(MessageConstantsV3.MsgMapSourceLocation);
        out.writeInt(sourceLocationId);
        out.writeInt(sigId);
        out.writeInt(startLine);
        out.writeInt(endLine);
        out.writeShort(startCharacter);
        out.writeShort(endCharacter);
    }

    @Override
    public void writeMethodVisit(DataOutputStream out, int relTime, int seq, int sigId, int sourceLocationId, int threadId) throws IOException, NotSupportedException
    {
        out.writeByte(MessageConstantsV3.MsgMethodVisit);
        out.writeInt(relTime);
        out.writeInt(seq);
        out.writeInt(sigId);
        out.writeInt(sourceLocationId);
        out.writeShort(threadId);
    }
}

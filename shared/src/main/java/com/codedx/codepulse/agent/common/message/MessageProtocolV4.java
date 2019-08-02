package com.codedx.codepulse.agent.common.message;

import java.io.DataOutputStream;
import java.io.IOException;

public class MessageProtocolV4 extends MessageProtocolV3 {

	@Override
	public byte protocolVersion()
	{
		return 4;
	}

	@Override
	public void writeProjectHello(DataOutputStream out, int projectId) throws IOException
	{
		out.writeByte(MessageConstantsV4.MsgProjectHello);
		out.writeByte(4); // protocol version (next)
		out.writeInt(projectId);
	}
}

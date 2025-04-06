package gg.playit.proto.control.c2s;

import gg.playit.proto.control.AgentSessionId;
import io.netty.buffer.ByteBuf;

public record SetupUdpChannelControlRequest(AgentSessionId session_id) implements ControlRequest {
    @Override
    public void writeTo(ByteBuf buf) {
        buf.writeInt(4);
        session_id.writeTo(buf);
    }
}

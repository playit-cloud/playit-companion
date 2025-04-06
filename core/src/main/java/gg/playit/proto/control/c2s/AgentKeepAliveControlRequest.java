package gg.playit.proto.control.c2s;

import gg.playit.proto.control.AgentSessionId;
import io.netty.buffer.ByteBuf;

public record AgentKeepAliveControlRequest(AgentSessionId session_id) implements ControlRequest {
    @Override
    public void writeTo(ByteBuf buf) {
        buf.writeInt(3);
        session_id.writeTo(buf);
    }
}

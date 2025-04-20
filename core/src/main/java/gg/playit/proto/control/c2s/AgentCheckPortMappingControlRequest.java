package gg.playit.proto.control.c2s;

import gg.playit.proto.control.AgentSessionId;
import gg.playit.proto.control.PortRange;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record AgentCheckPortMappingControlRequest(AgentSessionId session_id, PortRange port_range) implements ControlRequest {
    @Override
    public void writeTo(ByteBuf buf) throws IOException {
        session_id.writeTo(buf);
        port_range.writeTo(buf);
    }
}
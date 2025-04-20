package gg.playit.proto.control.s2c;

import gg.playit.proto.control.AgentSessionId;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record AgentPortMappingFound(AgentSessionId session_id) {
    public static AgentPortMappingFound from(ByteBuf buffer) throws IOException {
        if (buffer.readInt() != 1) {
            throw new IOException("Unknown AgentPortMappingFound id");
        }
        return new AgentPortMappingFound(new AgentSessionId(buffer));
    }
}

package gg.playit.proto.control.s2c;

import gg.playit.proto.control.AgentSessionId;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record AgentPortMappingControlResponse() implements ControlResponse {
    public static AgentPortMappingControlResponse from(ByteBuf buffer) throws IOException {
        throw new UnsupportedOperationException("AgentPortMappingControlResponse is a TODO!");
    }
}

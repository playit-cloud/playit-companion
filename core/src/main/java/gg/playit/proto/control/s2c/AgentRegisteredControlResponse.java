package gg.playit.proto.control.s2c;

import gg.playit.proto.control.AgentSessionId;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.OptionalLong;

public record AgentRegisteredControlResponse(AgentSessionId id, long expires_at) implements ControlResponse {
    public static AgentRegisteredControlResponse from(ByteBuf buffer) throws IOException {
        var id = new AgentSessionId(buffer);
        var expires_at = buffer.readLong();
        return new AgentRegisteredControlResponse(id, expires_at);
    }
}

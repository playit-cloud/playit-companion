package gg.playit.proto.control.c2s;

import gg.playit.proto.control.AgentSessionId;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;

public record PingControlRequest(long now, OptionalInt current_ping, Optional<AgentSessionId> session_id) implements ControlRequest {
    @Override
    public void writeTo(ByteBuf buf) throws IOException {
        buf.writeInt(6);
        buf.writeLong(now);
        if (current_ping.isPresent()) {
            buf.writeByte(1);
            buf.writeInt(current_ping.getAsInt());
        } else {
            buf.writeByte(0);
        }
        WireWritable.writeOptional(buf, session_id);
    }
}

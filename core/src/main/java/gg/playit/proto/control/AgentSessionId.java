package gg.playit.proto.control;

import gg.playit.proto.control.c2s.WireWritable;
import io.netty.buffer.ByteBuf;

public record AgentSessionId(long session_id, long account_id, long agent_id) implements WireWritable {
    public AgentSessionId(ByteBuf buf) {
        this(buf.readLong(), buf.readLong(), buf.readLong());
    }

    @Override
    public void writeTo(ByteBuf buf) {
        buf.writeLong(session_id);
        buf.writeLong(account_id);
        buf.writeLong(agent_id);
    }
}

package gg.playit.proto.control.c2s;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record ControlRpcRequest(long request_id, WireWritable content) implements WireWritable {
    @Override
    public void writeTo(ByteBuf buf) throws IOException {
        buf.writeLong(request_id);
        content.writeTo(buf);
    }
}

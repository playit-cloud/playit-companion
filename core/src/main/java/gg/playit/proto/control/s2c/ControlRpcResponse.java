package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record ControlRpcResponse(long request_id, ControlResponse content) implements WireReadable {
    public static ControlRpcResponse from(ByteBuf buffer) throws IOException {
        long request_id = buffer.readLong();
        var content = ControlResponse.from(buffer);
        return new ControlRpcResponse(request_id, content);
    }
}

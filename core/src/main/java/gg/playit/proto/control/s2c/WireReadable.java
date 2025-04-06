package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public sealed interface WireReadable permits ControlRpcResponse, NewClient {
    static WireReadable from(ByteBuf buffer) throws IOException {
        return switch (buffer.readInt()) {
            case 1 -> ControlRpcResponse.from(buffer);
            case 2 -> NewClient.from(buffer);
            default -> throw new IOException("bad Option discrim");
        };
    }
}

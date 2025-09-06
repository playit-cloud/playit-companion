package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public sealed interface WireReadable permits ControlRpcResponse, NewClient, NewClientOld {
    static WireReadable from(ByteBuf buffer) throws IOException {
        return switch (buffer.readInt()) {
            case 1 -> ControlRpcResponse.from(buffer);
            case 2 -> NewClientOld.from(buffer);
            case 3 -> NewClient.from(buffer);
            default -> throw new IOException("bad Option discrim");
        };
    }
}

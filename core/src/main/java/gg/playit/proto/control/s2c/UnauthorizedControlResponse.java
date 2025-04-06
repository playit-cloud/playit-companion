package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

public record UnauthorizedControlResponse() implements ControlResponse {
    public static UnauthorizedControlResponse from(ByteBuf buffer) {
        return new UnauthorizedControlResponse();
    }
}

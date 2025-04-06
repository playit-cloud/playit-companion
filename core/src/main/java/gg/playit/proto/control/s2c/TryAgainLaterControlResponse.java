package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

public record TryAgainLaterControlResponse() implements ControlResponse {
    public static TryAgainLaterControlResponse from(ByteBuf buffer) {
        return new TryAgainLaterControlResponse();
    }
}

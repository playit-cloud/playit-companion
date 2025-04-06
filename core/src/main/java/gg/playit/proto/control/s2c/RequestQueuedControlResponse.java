package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

public record RequestQueuedControlResponse() implements ControlResponse {
    public static RequestQueuedControlResponse from(ByteBuf buffer) {
        return new RequestQueuedControlResponse();
    }
}

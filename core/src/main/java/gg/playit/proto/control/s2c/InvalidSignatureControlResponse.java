package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record InvalidSignatureControlResponse() implements ControlResponse {
    public static InvalidSignatureControlResponse from(ByteBuf buffer) {
        return new InvalidSignatureControlResponse();
    }
}

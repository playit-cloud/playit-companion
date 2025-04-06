package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record UdpChannelDetailsControlResponse() implements ControlResponse {
    public static UdpChannelDetailsControlResponse from(ByteBuf buffer) throws IOException {
        throw new UnsupportedOperationException("AgentPortMappingControlResponse is a TODO!");
    }
}

package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record UdpChannelDetailsControlResponse(SocketAddr tunnel_addr, byte[] token) implements ControlResponse {
    public static UdpChannelDetailsControlResponse from(ByteBuf buffer) throws IOException {
        var address = new SocketAddr(buffer);
        var len = buffer.readLong();
        var buf = new byte[(int) len];
        buffer.readBytes(buf);
        return new UdpChannelDetailsControlResponse(address, buf);
    }
}

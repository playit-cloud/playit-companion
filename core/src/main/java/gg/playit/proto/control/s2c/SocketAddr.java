package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;

public record SocketAddr(InetSocketAddress address) {
    public SocketAddr(ByteBuf buf) throws IOException {
        this(parse(buf));
    }

    private static InetSocketAddress parse(ByteBuf buffer) throws IOException {
        switch (buffer.readByte()) {
            case 4:
                var addrBuf4 = new byte[4];
                buffer.readBytes(addrBuf4);
                var ip4 = Inet4Address.getByAddress(addrBuf4);
                var port4 = buffer.readUnsignedShort();
                return new InetSocketAddress(ip4, port4);
            case 6:
                var addrBuf6 = new byte[16];
                buffer.readBytes(addrBuf6);
                var ip6 = Inet6Address.getByAddress(addrBuf6);
                var port6 = buffer.readUnsignedShort();
                return new InetSocketAddress(ip6, port6);
            default:
                throw new IOException("bad IP family");
        }
    }

    @Override
    public String toString() {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }
}

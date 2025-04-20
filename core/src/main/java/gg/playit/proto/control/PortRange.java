package gg.playit.proto.control;

import gg.playit.proto.control.c2s.WireWritable;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public record PortRange(InetAddress address, int port_start, int port_end, PortProto proto) implements WireWritable {
    @Override
    public void writeTo(ByteBuf buf) throws IOException {
        if (address instanceof Inet4Address) {
            buf.writeByte(4);
            buf.writeBytes(address.getAddress());
        } else if (address instanceof Inet6Address) {
            buf.writeByte(6);
            buf.writeBytes(address.getAddress());
        } else {
            throw new IOException("bad IP family");
        }
        buf.writeShort(port_start);
        buf.writeShort(port_end);
        proto.writeTo(buf);
    }

    public static PortRange from(ByteBuf buffer) throws IOException {
        InetAddress ip;
        switch (buffer.readByte()) {
            case 4:
                var addrBuf4 = new byte[4];
                buffer.readBytes(addrBuf4);
                ip = Inet4Address.getByAddress(addrBuf4);
                break;
            case 6:
                var addrBuf6 = new byte[16];
                buffer.readBytes(addrBuf6);
                ip = Inet6Address.getByAddress(addrBuf6);
                break;
            default:
                throw new IOException("bad IP family");
        }
        var port_start = buffer.readUnsignedShort();
        var port_end = buffer.readUnsignedShort();
        var port_proto = PortProto.from(buffer);
        return new PortRange(ip, port_start, port_end, port_proto);
    }
}

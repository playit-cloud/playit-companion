package gg.playit.proto.control;

import gg.playit.proto.control.c2s.WireWritable;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public enum PortProto implements WireWritable {
    TCP,
    UDP,
    BOTH;

    @Override
    public void writeTo(ByteBuf buf) throws IOException {
        switch (this) {
            case TCP -> buf.writeByte(1);
            case UDP -> buf.writeByte(2);
            case BOTH -> buf.writeByte(3);
        }
    }

    public static PortProto from(ByteBuf buffer) throws IOException {
        return switch (buffer.readInt()) {
            case 1 -> TCP;
            case 2 -> UDP;
            case 3 -> BOTH;
            default -> throw new IOException("bad PortProto");
        };
    }
}
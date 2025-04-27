package gg.playit;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HexFormat;

public record RoutableDatagramPacket(InetSocketAddress source, InetSocketAddress destination, byte[] contents) {
    @Override
    public String toString() {
        return "RoutableDatagramPacket{" +
                "source=" + source +
                ", destination=" + destination +
                ", contents=" + HexFormat.of().formatHex(contents) +
                '}';
    }
}

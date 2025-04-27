package gg.playit;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;

public record SimpleSocketAddress(byte[] ip, int port) {
    public static SimpleSocketAddress from(SocketAddress addr) {
        if (addr instanceof InetSocketAddress socketAddress) {
            return new SimpleSocketAddress(socketAddress.getAddress().getAddress(), socketAddress.getPort());
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SimpleSocketAddress that = (SimpleSocketAddress) o;
        return port == that.port && Objects.deepEquals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(ip), port);
    }
}

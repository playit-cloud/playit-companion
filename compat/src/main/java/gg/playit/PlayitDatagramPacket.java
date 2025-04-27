package gg.playit;

import de.maxhenkel.voicechat.api.RawUdpPacket;

import java.net.SocketAddress;

public record PlayitDatagramPacket(byte[] data, long timestamp, SocketAddress socketAddress) implements RawUdpPacket {
    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }
}

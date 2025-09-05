package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record NewClient(SocketAddr connect_addr, SocketAddr peer_addr, int data_center_id, long tunnel_id, int port_offset, ClaimInstructions claim_instructions) implements WireReadable {
    public static NewClient from(ByteBuf buffer) throws IOException {
        var connect_addr = new SocketAddr(buffer);
        var peer_addr = new SocketAddr(buffer);
        var data_center_id = buffer.readInt();
        var tunnel_id = buffer.readLong();
        var port_offset = buffer.readUnsignedShort();
        var claim_instructions = ClaimInstructions.from(buffer);
        return new NewClient(connect_addr, peer_addr, data_center_id, tunnel_id, port_offset, claim_instructions);
    }
}

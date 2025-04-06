package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public record NewClient(SocketAddr connect_addr, SocketAddr peer_addr, ClaimInstructions claim_instructions, long tunnel_server_id, int data_center_id) implements WireReadable {
    public static NewClient from(ByteBuf buffer) throws IOException {
        var connect_addr = new SocketAddr(buffer);
        var peer_addr = new SocketAddr(buffer);
        var claim_instructions = ClaimInstructions.from(buffer);
        var tunnel_server_id = buffer.readLong();
        var data_center_id = buffer.readInt();
        return new NewClient(connect_addr, peer_addr, claim_instructions, tunnel_server_id, data_center_id);
    }
}

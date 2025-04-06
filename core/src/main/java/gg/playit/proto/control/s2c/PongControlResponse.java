package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.OptionalLong;

public record PongControlResponse(long request_now, long server_now, long server_id, long data_center_id, SocketAddr client_addr, SocketAddr tunnel_addr, OptionalLong session_expire_at) implements ControlResponse {
    public static PongControlResponse from(ByteBuf buffer) throws IOException {
        var request_now = buffer.readLong();
        var server_now = buffer.readLong();
        var server_id = buffer.readLong();
        var data_center_id = buffer.readInt();
        var client_addr = new SocketAddr(buffer);
        var tunnel_addr = new SocketAddr(buffer);
        OptionalLong session_expire_at = switch (buffer.readByte()) {
            case 1 -> OptionalLong.of(buffer.readLong());
            case 0 -> OptionalLong.empty();
            default -> throw new IOException("bad Option discrim");
        };
        return new PongControlResponse(request_now, server_now, server_id, data_center_id, client_addr, tunnel_addr, session_expire_at);
    }
}

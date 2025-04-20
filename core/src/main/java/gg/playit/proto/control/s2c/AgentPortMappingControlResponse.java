package gg.playit.proto.control.s2c;

import gg.playit.proto.control.PortRange;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.Optional;

public record AgentPortMappingControlResponse(PortRange range, Optional<AgentPortMappingFound> found) implements ControlResponse {
    public static AgentPortMappingControlResponse from(ByteBuf buffer) throws IOException {
        var range = PortRange.from(buffer);
        Optional<AgentPortMappingFound> found = switch (buffer.readByte()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(AgentPortMappingFound.from(buffer));
            default -> throw new IOException("Invalid Option value");
        };
        return new AgentPortMappingControlResponse(range, found);
    }
}

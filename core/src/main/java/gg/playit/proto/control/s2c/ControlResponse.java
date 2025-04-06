package gg.playit.proto.control.s2c;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.OptionalLong;

public sealed interface ControlResponse permits AgentPortMappingControlResponse, AgentRegisteredControlResponse, InvalidSignatureControlResponse, PongControlResponse, RequestQueuedControlResponse, TryAgainLaterControlResponse, UdpChannelDetailsControlResponse, UnauthorizedControlResponse {
    static ControlResponse from(ByteBuf buffer) throws IOException {
        return switch (buffer.readInt()) {
            case 1 -> PongControlResponse.from(buffer);
            case 2 -> InvalidSignatureControlResponse.from(buffer);
            case 3 -> UnauthorizedControlResponse.from(buffer);
            case 4 -> RequestQueuedControlResponse.from(buffer);
            case 5 -> TryAgainLaterControlResponse.from(buffer);
            case 6 -> AgentRegisteredControlResponse.from(buffer);
            case 7 -> AgentPortMappingControlResponse.from(buffer);
            case 8 -> UdpChannelDetailsControlResponse.from(buffer);
            default -> throw new IOException("bad Option discrim");
        };
    }
}

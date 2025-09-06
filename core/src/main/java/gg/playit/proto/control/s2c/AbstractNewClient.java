package gg.playit.proto.control.s2c;

import gg.playit.proto.rest.AgentTunnel;

public interface AbstractNewClient {
    boolean matches(AgentTunnel tunnel);
    ClaimInstructions claim_instructions();
    SocketAddr connect_addr();
    SocketAddr peer_addr();
}

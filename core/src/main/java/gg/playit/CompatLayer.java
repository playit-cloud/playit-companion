package gg.playit;

import gg.playit.proto.rest.AgentTunnel;

public sealed interface CompatLayer permits DatagramCompatLayer, SocketCompatLayer {
    String protocolName();
    void tunnelUpdated(AgentTunnel tunnel);
}

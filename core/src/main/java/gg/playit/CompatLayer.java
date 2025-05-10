package gg.playit;

import gg.playit.proto.rest.AgentTunnel;

public sealed interface CompatLayer permits DatagramCompatLayer, DisabledCompatLayer, SocketCompatLayer {
    String protocolName();
    void tunnelUpdated(AgentTunnel tunnel);
}

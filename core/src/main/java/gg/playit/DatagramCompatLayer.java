package gg.playit;

import gg.playit.proto.rest.AgentTunnel;

import java.util.function.Consumer;

public non-sealed interface DatagramCompatLayer extends CompatLayer {
    void tunnelAssigned(Consumer<RoutableDatagramPacket> packetSender, AgentTunnel tunnel);
    void receivedPacket(RoutableDatagramPacket packet);
}
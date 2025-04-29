package gg.playit;

import gg.playit.proto.rest.AgentTunnel;

import java.util.function.Consumer;

public non-sealed interface DatagramCompatLayer extends CompatLayer {
    void datagramStarted(Consumer<RoutableDatagramPacket> packetSender);
    void receivedPacket(RoutableDatagramPacket packet);
}
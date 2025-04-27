package gg.playit;

import gg.playit.proto.rest.AgentTunnel;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public non-sealed interface SocketCompatLayer extends CompatLayer {
    void tunnelAssigned(AgentTunnel tunnel);
    void receivedConnection(InetSocketAddress localAddress, InetSocketAddress remoteAddress, SocketChannel channel);
}
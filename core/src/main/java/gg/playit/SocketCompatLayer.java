package gg.playit;

import gg.playit.proto.rest.AgentTunnel;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;

public non-sealed interface SocketCompatLayer extends CompatLayer {
    void tunnelAssigned(AgentTunnel tunnel);
    void receivedConnection(InetSocketAddress localAddress, InetSocketAddress remoteAddress, SocketChannel channel);
}
package gg.playit;

public sealed interface CompatLayer permits DatagramCompatLayer, SocketCompatLayer {
    String protocolName();
}

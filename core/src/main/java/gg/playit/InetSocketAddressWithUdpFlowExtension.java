package gg.playit;

import java.net.InetSocketAddress;

public record InetSocketAddressWithUdpFlowExtension(InetSocketAddress address, UdpFlowExtension flow) {
}

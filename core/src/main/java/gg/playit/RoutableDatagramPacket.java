package gg.playit;

import gg.playit.proto.rest.AgentTunnel;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.HexFormat;

public record RoutableDatagramPacket(UdpFlowExtension flow, InetSocketAddress source, InetSocketAddress destination, byte[] contents) {
    @Override
    public String toString() {
        return "RoutableDatagramPacket{" +
                "source=" + source +
                ", destination=" + destination +
                ", contents=" + HexFormat.of().formatHex(contents) +
                '}';
    }

    public boolean matches(AgentTunnel tunnel) {
        if (flow == null) {
            var portMatches = destination.getPort() >= tunnel.port.from && destination.getPort() < tunnel.port.to;
            int addr_region;
            long addr_num;
            if (destination.getAddress() instanceof Inet4Address inet4Address) {
                var addr = inet4Address.getAddress();
                if (addr[0] == 0 && addr[1] == 0 && addr[2] == 0) {
                    addr_region = 0;
                } else if (addr[0] == (byte) 147 && addr[1] == (byte) 185 && addr[2] == (byte) 221) {
                    addr_region = 1;
                } else if (addr[0] == (byte) 209 && addr[1] == (byte) 25 && addr[2] == (byte) 140) {
                    addr_region = 2;
                } else if (addr[0] == (byte) 209 && addr[1] == (byte) 25 && addr[2] == (byte) 141) {
                    addr_region = 3;
                } else if (addr[0] == (byte) 209 && addr[1] == (byte) 25 && addr[2] == (byte) 142) {
                    addr_region = 4;
                } else if (addr[0] == (byte) 209 && addr[1] == (byte) 25 && addr[2] == (byte) 143) {
                    addr_region = 5;
                } else if (addr[0] == (byte) 23 && addr[1] == (byte) 133 && addr[2] == (byte) 216) {
                    addr_region = 6;
                } else if (addr[0] == (byte) 198 && addr[1] == (byte) 22 && addr[2] == (byte) 204) {
                    addr_region = 6;
                } else {
                    return false;
                }
                addr_num = addr[3];
            } else if (destination.getAddress() instanceof Inet6Address inet6Address) {
                var addr = inet6Address.getAddress();
                addr_region = (((int) addr[6]) << 8) | ((int) addr[7]);
                addr_num = (((long) addr[8]) << 56) | (((long) addr[9]) << 48) | (((long) addr[10]) << 40) | (((long) addr[11]) << 32) | (((long) addr[12]) << 24) | (((long) addr[13]) << 16) | (((long) addr[14]) << 8) | ((long) addr[15]);
            } else {
                return false;
            }
            return portMatches && addr_region == tunnel.region_num && addr_num == tunnel.ip_num;
        } else {
            return flow.tunnel_id() == tunnel.internal_id;
        }
    }
}

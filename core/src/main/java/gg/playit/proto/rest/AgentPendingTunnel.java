package gg.playit.proto.rest;

public record AgentPendingTunnel(
        String id,
        String name,
        String proto,
        int port_count,
        String tunnel_type,
        boolean is_disabled,
        int region_num) {
}

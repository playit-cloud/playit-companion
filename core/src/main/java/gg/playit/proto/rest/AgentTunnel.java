package gg.playit.proto.rest;

public record AgentTunnel(
        String id,
        String name,
        long ip_num,
        int region_num,
        PortRange port,
        String proto,
        String local_ip,
        int local_port,
        String tunnel_type,
        String assigned_domain,
        String custom_domain,
        AgentTunnelDisabled disabled,
        String proxy_protocol) {
}

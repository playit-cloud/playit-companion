package gg.playit.proto.rest;

public record TunnelsCreateRequest(String name, String tunnel_type, String port_type, int port_count, TunnelOriginCreate origin, boolean enabled, TunnelCreateUseAllocation alloc) {
}

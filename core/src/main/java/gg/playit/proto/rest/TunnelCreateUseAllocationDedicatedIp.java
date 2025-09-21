package gg.playit.proto.rest;

public record TunnelCreateUseAllocationDedicatedIp(String ip_hostname, int port) implements TunnelCreateUseAllocationPayload {
}

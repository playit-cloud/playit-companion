package gg.playit.proto.rest;

public record ProtoRegisterRequest(PlayitAgentVersion version, long proto_version, String platform, String client_addr, String tunnel_addr) {
}

package gg.playit.proto.rest;

public record ProtoRegisterRequest(PlayitAgentVersion agent_version, String client_addr, String tunnel_addr) {
}

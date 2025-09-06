package gg.playit.proto.rest;

public record PlayitAgentVersion(AgentVersion version, boolean official, String details_website, long proto_version) {
}

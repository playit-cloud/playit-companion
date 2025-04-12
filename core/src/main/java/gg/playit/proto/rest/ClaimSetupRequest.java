package gg.playit.proto.rest;

public record ClaimSetupRequest(String code, String agent_type, String version) {
}
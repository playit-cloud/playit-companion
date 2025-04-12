package gg.playit.proto.rest;

import java.util.List;

public record AgentRoutingGetResponse(String agent_id, List<String> targets4, List<String> targets6, boolean disable_ip6) {
}

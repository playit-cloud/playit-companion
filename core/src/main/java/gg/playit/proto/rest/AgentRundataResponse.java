package gg.playit.proto.rest;

import java.util.List;

public record AgentRundataResponse(String agent_id, String agent_type, String account_status, List<AgentTunnel> tunnels, List<AgentPendingTunnel> pending, AccountFeatures account_features) {
}

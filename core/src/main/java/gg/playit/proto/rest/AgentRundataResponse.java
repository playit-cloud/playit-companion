package gg.playit.proto.rest;

import java.util.List;

public class AgentRundataResponse {
    public String agent_id;
    public String agent_type;
    public String account_status;
    public List<AgentTunnel> tunnels;
    public List<AgentPendingTunnel> pending;
    public AccountFeatures account_features;
}

package gg.playit.proto.rest;

import java.util.List;

public class AgentRoutingGetResponse {
    public String agent_id;
    public List<String> targets4;
    public List<String> targets6;
    public boolean disable_ip6;
}

package gg.playit.proto.rest;

public class AgentTunnel {
    public String id;
    public long internal_id;
    public String name;
    public long ip_num;
    public int region_num;
    public PortRange port;
    public String proto;
    public String local_ip;
    public int local_port;
    public String tunnel_type;
    public String assigned_domain;
    public String custom_domain;
    public AgentTunnelDisabled disabled;
    public String proxy_protocol;
}

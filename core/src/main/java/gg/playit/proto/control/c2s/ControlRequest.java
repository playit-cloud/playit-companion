package gg.playit.proto.control.c2s;

public sealed interface ControlRequest extends WireWritable permits AgentCheckPortMappingControlRequest, AgentKeepAliveControlRequest, PingControlRequest, SetupUdpChannelControlRequest {
}

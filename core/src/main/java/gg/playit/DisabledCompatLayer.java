package gg.playit;

import gg.playit.proto.rest.AgentTunnel;

final class DisabledCompatLayer implements CompatLayer {
    @Override
    public String protocolName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void tunnelUpdated(AgentTunnel tunnel) {
        throw new UnsupportedOperationException();
    }
}

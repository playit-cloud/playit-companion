package gg.playit;

import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;

public interface Platform {
    Logger getLogger();
    String getVersion();
    void newMinecraftConnection(InetSocketAddress peer_address, NioSocketChannel channel);
    String getAgentKey() throws IOException;
    void writeAgentKey(String agentKey) throws IOException;
}

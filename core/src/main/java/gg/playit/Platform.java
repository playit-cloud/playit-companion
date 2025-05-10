package gg.playit;

import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public interface Platform {
    Logger getLogger();
    String getVersion();
    void newMinecraftConnection(InetSocketAddress peer_address, SocketChannel channel);
    String getAgentKey() throws IOException;
    void writeAgentKey(String agentKey) throws IOException;
    void tunnelAddressInformation(String addr);
    void notifyError();
    boolean shouldUseEpoll();
    Path getCustomTunnelsConfigPath();
}

package gg.playit;

import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

public interface Platform {
    Logger getLogger();
    String getVersion();
    void newMinecraftConnection(InetSocketAddress peer_address, NioSocketChannel channel);
}

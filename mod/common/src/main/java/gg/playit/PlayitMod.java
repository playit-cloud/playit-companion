package gg.playit;

import gg.playit.mixin.ConnectionAccessor;
import gg.playit.mixin.ServerConnectionListenerChildHandlerAccessor;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;

public class PlayitMod {
    public static final String MOD_ID = "playit_companion";
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayitMod.MOD_ID);

    private static PlayitAgent agent;
    private static Timer timer = new Timer();

    public static void init() {
    }

    public static void startAsync(MinecraftServer server, ServerConnectionListenerChildHandlerAccessor accessor) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                start(server, accessor);
            }
        }, 0);
    }

    public static void start(MinecraftServer server, ServerConnectionListenerChildHandlerAccessor accessor) {
        var agentKeyPath = Agnos.configDir().resolve("playit-companion").resolve("agent_key.txt");
        var configPath = Agnos.configDir().resolve("playit-companion").resolve("custom_tunnels.css");
        agent = new PlayitAgent(new Platform() {
            @Override
            public Logger getLogger() {
                return LOGGER;
            }

            @Override
            public String getVersion() {
                return Agnos.version();
            }

            @Override
            public void newMinecraftConnection(InetSocketAddress peer_address, SocketChannel channel) {
                accessor.callInitChannel(channel);
                ((ConnectionAccessor) channel.pipeline().get(Connection.class)).setAddress(peer_address);
            }

            @Override
            public String getAgentKey() throws IOException {
                if (Files.exists(agentKeyPath)) {
                    return Files.readString(agentKeyPath, StandardCharsets.UTF_8).strip();
                } else {
                    return null;
                }
            }

            @Override
            public void writeAgentKey(String agentKey) throws IOException {
                Files.createDirectories(agentKeyPath.getParent());
                Files.writeString(agentKeyPath, agentKey, StandardCharsets.UTF_8);
            }

            @Override
            public void tunnelAddressInformation(String addr) {
                var addrComponent = VersionArbitrage.withStyle(VersionArbitrage.literal(addr),
                        style -> style
                                .withClickEvent(VersionArbitrage.copyToClipboard(addr))
                                .withHoverEvent(VersionArbitrage.showText(VersionArbitrage.translatable("chat.copy.click")))
                                .withColor(ChatFormatting.GREEN)
                        );
                VersionArbitrage.broadcast(server, VersionArbitrage.translatable("playit.domain", addrComponent));
            }

            @Override
            public void notifyError() {
                VersionArbitrage.broadcast(server, VersionArbitrage.translatable("playit.error"));
            }

            @Override
            public boolean shouldUseEpoll() {
                return Epoll.isAvailable() && server.isEpollEnabled();
            }

            @Override
            public Path getCustomTunnelsConfigPath() {
                return configPath;
            }
        });

        if (agent.getClaimCode() != null) {
            var url = "https://playit.gg/claim/" + agent.getClaimCode();
            LOGGER.info(url);
            var urlComponent = VersionArbitrage.withStyle(
                    VersionArbitrage.literal(url),style -> style
                            .withClickEvent(VersionArbitrage.openUrl(url))
                            .withHoverEvent(VersionArbitrage.showText(VersionArbitrage.translatable("chat.link.open")))
                            .withColor(ChatFormatting.BLUE)
                    );
            VersionArbitrage.broadcast(server, VersionArbitrage.translatable("playit.claim", urlComponent));
        }

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    switch (agent.claimStep()) {
                        case Accepted:
                            agent.run();
                            timer.cancel();
                            timer = new Timer();
                            break;
                        case Rejected:
                            VersionArbitrage.broadcast(server, VersionArbitrage.translatable("playit.rejected"));
                            timer.cancel();
                            timer = new Timer();
                            break;
                        case NotDone:
                            break;
                    }
                } catch (Exception e) {
                    LOGGER.error("Error running agent", e);
                    VersionArbitrage.broadcast(server, VersionArbitrage.translatable("playit.error"));
                    timer.cancel();
                }
            }
        }, 0, 500);
    }



    public static void stop() {
        if (agent != null) {
            timer.cancel();
            timer = new Timer();
            try {
                agent.close();
            } catch (IOException ignored) {}
            agent = null;
        }
    }
}

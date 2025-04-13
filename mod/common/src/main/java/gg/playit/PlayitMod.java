package gg.playit;

import gg.playit.mixin.ConnectionAccessor;
import gg.playit.mixin.ServerConnectionListenerChildHandlerAccessor;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Timer;
import java.util.TimerTask;

public class PlayitMod {
    public static final String MOD_ID = "playit-companion";
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayitMod.MOD_ID);

    private static PlayitAgent agent;
    private static Timer timer = new Timer();

    public static void init() {
//        if (System.getProperty("os.name").startsWith("Windows")) {
//            var path = Agnos.jarPath();
//            var motwPath = path + ":Zone.Identifier";
//            try(FileInputStream inputStream = new FileInputStream(motwPath)) {
//                String hidden = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
//                LOGGER.warn(hidden);
//            } catch (IOException ignored) {}
//        }
    }

    public static void start(MinecraftServer server, ServerConnectionListenerChildHandlerAccessor accessor) {
        var agentKeyPath = Agnos.configDir().resolve("playit-companion").resolve("agent_key.txt");
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
            public void newMinecraftConnection(InetSocketAddress peer_address, NioSocketChannel channel) {
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
                var addrComponent = Component
                        .literal(addr)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, addr))
                                .withColor(ChatFormatting.GREEN)
                        );
                server.getPlayerList().broadcastSystemMessage(Component.translatable("playit.domain", addrComponent), false);
            }
        });

        if (agent.getClaimCode() != null) {
            var url = "https://playit.gg/claim/" + agent.getClaimCode();
            var urlComponent = Component
                    .literal(url)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withColor(ChatFormatting.BLUE)
                    );
            server.getPlayerList().broadcastSystemMessage(Component.translatable("playit.claim", urlComponent), false);
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
                            server.getPlayerList().broadcastSystemMessage(Component.literal("Agent registration rejected!"), false);
                            timer.cancel();
                            timer = new Timer();
                            break;
                        case NotDone:
                            break;
                    }
                } catch (Exception e) {
                    LOGGER.error("Error running agent", e);
                }
            }
        }, 0, 500);
    }



    public static void stop() {
        if (agent != null) {
            try {
                agent.close();
            } catch (IOException ignored) {}
            timer.cancel();
            timer = new Timer();

            agent = null;
        }
    }
}

package gg.playit;

import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PlayitPlugin extends JavaPlugin {
    private PlayitAgent agent;
    private ScheduledTask task;

    @Override
    public void onEnable() {
        try {
            var agentKeyPath = getDataPath().resolve("agent_key.txt");

            var clazz = (Class<? extends ChannelHandler>) Class.forName("io.netty.bootstrap.ServerBootstrap$ServerBootstrapAcceptor"); // This inner class is private
            var nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
            var scl = nmsServer.getConnection();
            Field channelsField = ServerConnectionListener.class.getDeclaredField("channels");
            channelsField.setAccessible(true);
            var channels = (List<ChannelFuture>) channelsField.get(scl);
            var handler = channels.getFirst().channel().pipeline().get(clazz);
            var childHandlerField = clazz.getDeclaredField("childHandler");
            childHandlerField.setAccessible(true);
            var childHandler = (ChannelInitializer) childHandlerField.get(handler);
            var initChannelMethod = childHandler.getClass().getDeclaredMethod("initChannel", Channel.class);
            initChannelMethod.setAccessible(true);
            agent = new PlayitAgent(new Platform() {
                @Override
                public Logger getLogger() {
                    return getSLF4JLogger();
                }

                @Override
                public String getVersion() {
                    return getPluginMeta().getVersion();
                }

                @Override
                public void newMinecraftConnection(InetSocketAddress peer_address, SocketChannel channel) {
                    try {
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                super.channelActive(ctx);
                                var connection = ctx.pipeline().get(Connection.class);
                                Field addrField = Connection.class.getDeclaredField("address");
                                addrField.setAccessible(true);
                                addrField.set(connection, peer_address);
                                ctx.pipeline().remove(this);
                            }
                        });
                        initChannelMethod.invoke(childHandler, channel);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
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
                    Files.createDirectories(getDataPath());
                    Files.writeString(agentKeyPath, agentKey, StandardCharsets.UTF_8);
                }

                @Override
                public void tunnelAddressInformation(String addr) {
                    Bukkit.getServer().sendPlainMessage("Server available at " + addr);
                }

                @Override
                public void notifyError() {
                    Bukkit.getServer().sendPlainMessage("An error has occurred in playit. Check logs for more details.");
                }

                @Override
                public boolean shouldUseEpoll() {
                    return Epoll.isAvailable() && nmsServer.isEpollEnabled();
                }
            });

            if (agent.getClaimCode() != null) {
                Bukkit.getServer().sendPlainMessage("https://playit.gg/claim/" + agent.getClaimCode());
            }

            task = Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
                try {
                    switch (agent.claimStep()) {
                        case Accepted:
                            agent.run();
                            task.cancel();
                            break;
                        case Rejected:
                            Bukkit.getServer().sendPlainMessage("Agent registration rejected!");
                            task.cancel();
                            break;
                        case NotDone:
                            break;
                    }
                } catch (Exception e) {
                    getSLF4JLogger().error("Error running agent", e);
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        task.cancel();
        try {
            agent.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

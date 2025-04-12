package gg.playit;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;

public class PlayitPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        try {
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
            PlayitAgent agent = new PlayitAgent(new Platform() {
                @Override
                public Logger getLogger() {
                    return getSLF4JLogger();
                }

                @Override
                public String getVersion() {
                    return getPluginMeta().getVersion();
                }

                @Override
                public void newMinecraftConnection(InetSocketAddress peer_address, NioSocketChannel channel) {
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
            }, "meow");
            agent.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

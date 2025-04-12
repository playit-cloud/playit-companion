package gg.playit;

import io.netty.channel.*;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
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
            PlayitAgent agent = new PlayitAgent("meow");
            agent.run(((address, channel) -> {
                try {
                    channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            super.channelActive(ctx);
                            var connection = ctx.pipeline().get(Connection.class);
                            Field addrField = Connection.class.getDeclaredField("address");
                            addrField.setAccessible(true);
                            addrField.set(connection, address);
                            ctx.pipeline().remove(this);
                        }
                    });
                    initChannelMethod.invoke(childHandler, channel);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

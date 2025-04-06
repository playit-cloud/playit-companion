package gg.playit;

import io.netty.channel.*;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.List;

public class PlayitPlugin extends JavaPlugin {
    private static Class<? extends ChannelHandler> connectionClass = null;

    @Override
    public void onEnable() {
        try {
            // Warning: horrifying heuristics-based guessing below
            var clazz = (Class<? extends ChannelHandler>) Class.forName("io.netty.bootstrap.ServerBootstrap$ServerBootstrapAcceptor"); // This inner class is private
            var serverClass = Bukkit.getServer().getClass();
            var getServerMethod = serverClass.getMethod("getServer");
            var nmsServer = getServerMethod.invoke(Bukkit.getServer());
            var nmsServerClass = nmsServer.getClass();
            Method getConnectionMethod = null;
            // So, we're looking for the method named getConnection in mojmap
            // getConnection returns ServerConnectionListener in mojmap
            // ServerConnectionListener has a method called startTcpServerListener in mojmap with two args:
            // an InetAddress and an int.
            // If such a method exists, it's probably ServerConnectionListener.
            for (Method method : nmsServerClass.getMethods()) {
                if (method.getParameterCount() != 0)
                    continue;
                var maybeScl = method.getReturnType();
                for (Method maybeStartTcpServerListener : maybeScl.getDeclaredMethods()) {
                    if (maybeStartTcpServerListener.getParameterCount() == 2 && maybeStartTcpServerListener.getReturnType() == void.class) {
                        var paramTypes = maybeStartTcpServerListener.getParameterTypes();
                        if (paramTypes[0] == InetAddress.class && paramTypes[1] == int.class) {
                            getConnectionMethod = method;
                        }
                    }
                }
            }
            var scl = getConnectionMethod.invoke(nmsServer);
            Field channelsField = null;
            // The two genders: List<ChannelFuture> and List<Connection>
            for (Field field : scl.getClass().getDeclaredFields()) {
                if (field.getType().equals(List.class)
                        && field.getGenericType() instanceof ParameterizedType type
                        && type.getActualTypeArguments()[0].equals(ChannelFuture.class)) {
                    channelsField = field;
                }
                if (field.getType().equals(List.class)
                        && field.getGenericType() instanceof ParameterizedType type
                        && (!type.getActualTypeArguments()[0].equals(ChannelFuture.class))) {
                    connectionClass = (Class<? extends ChannelHandler>) type.getActualTypeArguments()[0];
                }
            }
            if (channelsField == null) {
                throw new RuntimeException("Couldn't locate channels field!");
            }
            if (connectionClass == null) {
                throw new RuntimeException("Couldn't locate Connection class!");
            }
            channelsField.setAccessible(true);

            var channels = (List<ChannelFuture>) channelsField.get(scl);
            var handler = channels.get(0).channel().pipeline().get(clazz);
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
                            var connection = ctx.pipeline().get(connectionClass);
                            Field addrField = null;
                            for (Field field : connectionClass.getDeclaredFields()) {
                                if (field.getType().equals(SocketAddress.class)) {
                                    addrField = field;
                                    break;
                                }
                            }
                            if (addrField == null) {
                                throw new RuntimeException("Couldn't locate address field!");
                            }
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

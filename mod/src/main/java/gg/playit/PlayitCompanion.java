package gg.playit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.Connection;
import org.apache.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class PlayitCompanion implements ModInitializer {
	public static final String MOD_ID = "playit-companion";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
			try {
				var clazz = (Class<? extends ChannelHandler>) Class.forName("io.netty.bootstrap.ServerBootstrap$ServerBootstrapAcceptor");
				var scl = minecraftServer.getConnection();
				Field channelsField = null;
				for (Field field : scl.getClass().getDeclaredFields()) {
					if (field.getType().equals(List.class)
							&& field.getGenericType() instanceof ParameterizedType type
							&& type.getActualTypeArguments()[0].equals(ChannelFuture.class)) {
						channelsField = field;
						break;
					}
				}
				if (channelsField == null) {
					throw new RuntimeException("Couldn't locate channels field!");
				}
				channelsField.setAccessible(true);

				var channels = (List<ChannelFuture>) channelsField.get(scl);
				var handler = channels.get(0).channel().pipeline().get(clazz);
				var childHandlerField = clazz.getDeclaredField("childHandler");
				childHandlerField.setAccessible(true);
				var childHandler = (ChannelInitializer) childHandlerField.get(handler);
				var initChannelMethod = childHandler.getClass().getDeclaredMethod("initChannel", Channel.class);
				initChannelMethod.setAccessible(true);
				LOGGER.info("this is horrible");
				PlayitAgent agent = new PlayitAgent("meow");
				agent.run(((address, channel) -> {
                    try {
						channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
							@Override
							public void channelActive(ChannelHandlerContext ctx) throws Exception {
								super.channelActive(ctx);
								var connection = ctx.pipeline().get(Connection.class);
								Field addrField = null;
								for (Field field : Connection.class.getDeclaredFields()) {
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
		});

		LOGGER.info("Hello Fabric world!");
	}
}
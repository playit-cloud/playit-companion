package gg.playit.mixin;

import gg.playit.PlayitMod;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerConnectionListener.class)
public class ServerConnectionListenerMixin {
    @Shadow @Final
    MinecraftServer server;

    @Redirect(method = "startTcpServerListener", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/ServerBootstrap;childHandler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;"))
    ServerBootstrap interceptHandler(ServerBootstrap instance, ChannelHandler childHandler) {
        PlayitMod.startAsync(server, (ServerConnectionListenerChildHandlerAccessor) childHandler);
        return instance.childHandler(childHandler);
    }
}

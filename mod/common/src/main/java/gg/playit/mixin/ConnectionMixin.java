package gg.playit.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.SocketAddress;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Shadow private SocketAddress address;

    @Redirect(method = "channelActive", at = @At(value = "INVOKE", target = "Lio/netty/channel/Channel;remoteAddress()Ljava/net/SocketAddress;"))
    SocketAddress redirectRemoteAddress(Channel instance) {
        return address == null ? instance.remoteAddress() : address;
    }
}

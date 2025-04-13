package gg.playit.mixin;

import io.netty.channel.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public interface ServerConnectionListenerChildHandlerAccessor {
    @Invoker
    void callInitChannel(Channel channel);
}

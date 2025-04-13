package gg.playit.forge;

import gg.playit.PlayitMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(PlayitMod.MOD_ID)
public class PlayitForge {
    public PlayitForge() {
        PlayitMod.init();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    void serverStopped(ServerStoppedEvent event) {
        PlayitMod.stop();
    }
}

package gg.playit.neoforge;

import gg.playit.PlayitMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(PlayitMod.MOD_ID)
public class PlayitNeoForge {
    public PlayitNeoForge() {
        PlayitMod.init();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    void serverStopped(ServerStoppedEvent event) {
        PlayitMod.stop();
    }
}

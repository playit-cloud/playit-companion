package gg.playit.fabric;

import gg.playit.PlayitMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public class PlayitFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayitMod.init();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PlayitMod.stop();
        });
    }
}

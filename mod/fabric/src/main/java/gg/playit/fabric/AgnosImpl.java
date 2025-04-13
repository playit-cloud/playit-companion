package gg.playit.fabric;

import gg.playit.PlayitMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class AgnosImpl {
    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(PlayitMod.MOD_ID).get().getMetadata().getVersion().getFriendlyString();
    }
}

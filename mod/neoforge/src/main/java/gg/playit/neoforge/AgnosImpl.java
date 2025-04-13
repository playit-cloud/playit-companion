package gg.playit.neoforge;

import gg.playit.PlayitMod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class AgnosImpl {
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static String version() {
        return FMLLoader.getLoadingModList().getMods().stream().filter(modInfo -> modInfo.getModId().equals(PlayitMod.MOD_ID)).findAny().get().getVersion().toString();
    }
}

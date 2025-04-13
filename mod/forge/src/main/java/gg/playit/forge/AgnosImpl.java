package gg.playit.forge;

import gg.playit.PlayitMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class AgnosImpl {
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static String version() {
        return FMLLoader.getLoadingModList().getMods().stream().filter(modInfo -> modInfo.getModId().equals(PlayitMod.MOD_ID)).findAny().get().getVersion().toString();
    }
}

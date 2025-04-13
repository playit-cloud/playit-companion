package gg.playit;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.nio.file.Path;

public class Agnos {
    @ExpectPlatform
    public static Path configDir() {
        return null;
    }

    @ExpectPlatform
    public static String version() { return null; }
}

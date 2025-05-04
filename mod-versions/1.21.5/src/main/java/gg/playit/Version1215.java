package gg.playit;

import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.util.function.UnaryOperator;

public class Version1215 {
    public static ClickEvent openUrl(String url) {
        return new ClickEvent.OpenUrl(URI.create(url));
    }

    public static ClickEvent copyToClipboard(String text) {
        return new ClickEvent.CopyToClipboard(text);
    }

    public static HoverEvent showText(Component text) {
        return new HoverEvent.ShowText(text);
    }

    public static MutableComponent withStyle(MutableComponent component, UnaryOperator<Style> operator) {
        return component.withStyle(operator);
    }

    public static MutableComponent append(MutableComponent component, Component other) {
        return component.append(other);
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }


    public static MutableComponent translatable(String text, Object... args) {
        return Component.translatable(text, args);
    }

    public static void broadcast(MinecraftServer minecraftServer, Component message) {
        var playerList = minecraftServer.getPlayerList();
        if (playerList != null) {
            playerList.broadcastSystemMessage(message, false);
        } else {
            minecraftServer.sendSystemMessage(message);
        }
    }
}

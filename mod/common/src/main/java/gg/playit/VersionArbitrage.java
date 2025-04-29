package gg.playit;

import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;

import java.util.function.UnaryOperator;

public class VersionArbitrage {
    public static ClickEvent openUrl(String url) {
        try {
            return Version118.openUrl(url);
        } catch (Throwable ignored) {}
        try {
            return Version119.openUrl(url);
        } catch (Throwable ignored) {}
        try {
            return Version120.openUrl(url);
        } catch (Throwable ignored) {}
        try {
            return Version1215.openUrl(url);
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not create a ClickEvent.OpenUrl!");
    }

    public static ClickEvent copyToClipboard(String text) {
        try {
            return Version118.copyToClipboard(text);
        } catch (Throwable ignored) {}
        try {
            return Version119.copyToClipboard(text);
        } catch (Throwable ignored) {}
        try {
            return Version120.copyToClipboard(text);
        } catch (Throwable ignored) {}
        try {
            return Version1215.copyToClipboard(text);
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not create a ClickEvent.CopyToClipboard!");
    }

    public static HoverEvent showText(Component text) {
        try {
            return Version118.showText(text);
        } catch (Throwable ignored) {}
        try {
            return Version119.showText(text);
        } catch (Throwable ignored) {}
        try {
            return Version120.showText(text);
        } catch (Throwable ignored) {}
        try {
            return Version1215.showText(text);
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not create a HoverEvent.ShowText!");
    }

    public static MutableComponent withStyle(MutableComponent component, UnaryOperator<Style> operator) {
        try {
            return Version118.withStyle(component, operator);
        } catch (Throwable ignored) {}
        try {
            return Version119.withStyle(component, operator);
        } catch (Throwable ignored) {}
        try {
            return Version120.withStyle(component, operator);
        } catch (Throwable ignored) {}
        try {
            return Version1215.withStyle(component, operator);
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not call MutableComponent.withStyle!");
    }

    public static MutableComponent append(MutableComponent component, Component other) {
        try {
            return Version118.append(component, other);
        } catch (Throwable ignored) {}
        try {
            return Version119.append(component, other);
        } catch (Throwable ignored) {}
        try {
            return Version120.append(component, other);
        } catch (Throwable ignored) {}
        try {
            return Version1215.append(component, other);
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not call MutableComponent.append!");
    }

    public static MutableComponent literal(String text) {
        try {
            return Version118.literal(text);
        } catch (Throwable ignored) {}
        try {
            return Version119.literal(text);
        } catch (Throwable ignored) {}
        try {
            return Version120.literal(text);
        } catch (Throwable ignored) {}
        try {
            return Version1215.literal(text);
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not create Component.literal!");
    }


    public static MutableComponent translatable(String text, Object... args) {
        try {
            return Version118.translatable(text, args);
        } catch (Throwable ignored) {}
        try {
            return Version119.translatable(text, args);
        } catch (Throwable ignored) {}
        try {
            return Version120.translatable(text, args);
        } catch (Throwable ignored) {}
        try {
            return Version1215.translatable(text, args);
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not create Component.translatable!");
    }

    public static void broadcast(MinecraftServer minecraftServer, Component message) {
        try {
            Version118.broadcast(minecraftServer, message);
            return;
        } catch (Throwable ignored) {}
        try {
            Version119.broadcast(minecraftServer, message);
            return;
        } catch (Throwable ignored) {}
        try {
            Version120.broadcast(minecraftServer, message);
            return;
        } catch (Throwable ignored) {}
        try {
            Version1215.broadcast(minecraftServer, message);
            return;
        } catch (Throwable ignored) {}
        throw new RuntimeException("Could not call PlayerList.broadcastSystemMessage!");
    }
}

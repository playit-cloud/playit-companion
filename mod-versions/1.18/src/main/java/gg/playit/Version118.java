package gg.playit;

import net.minecraft.Util;
import net.minecraft.network.chat.*;
import net.minecraft.server.players.PlayerList;

import java.util.function.UnaryOperator;

public class Version118 {
    public static ClickEvent openUrl(String url) {
        return new ClickEvent(ClickEvent.Action.OPEN_URL, url);
    }

    public static ClickEvent copyToClipboard(String text) {
        return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text);
    }

    public static HoverEvent showText(Component text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
    }

    public static MutableComponent withStyle(MutableComponent component, UnaryOperator<Style> operator) {
        return component.withStyle(operator);
    }

    public static MutableComponent append(MutableComponent component, Component other) {
        return component.append(other);
    }

    public static MutableComponent literal(String text) {
        return new TextComponent(text);
    }


    public static MutableComponent translatable(String text, Object... args) {
        return new TranslatableComponent(text, args);
    }

    public static void broadcast(PlayerList playerList, Component message) {
        playerList.broadcastMessage(message, ChatType.SYSTEM, Util.NIL_UUID);
    }
}

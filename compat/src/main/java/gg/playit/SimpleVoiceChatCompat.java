package gg.playit;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoiceHostEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartingEvent;

public class SimpleVoiceChatCompat implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "playit-companion";
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        var socket = new PlayitVoicechatSocket();
        PlayitAgent.registerCompatLayer(socket);
        registration.registerEvent(VoiceHostEvent.class, socket::setVoiceHost);
        registration.registerEvent(VoicechatServerStartingEvent.class, ev -> {
            ev.setSocketImplementation(socket);
        });
    }
}

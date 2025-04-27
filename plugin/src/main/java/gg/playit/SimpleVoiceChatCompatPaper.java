package gg.playit;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;

public class SimpleVoiceChatCompatPaper {
    public static void tryRegister(PlayitPlugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            service.registerPlugin(new SimpleVoiceChatCompat());
        }
    }
}

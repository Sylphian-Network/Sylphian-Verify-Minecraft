package net.sylphian.verify.paper.listener;

import net.sylphian.verify.common.MessageUtils;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.paper.VerifyPaper;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class VerifyPluginMessageListener implements PluginMessageListener {
    private final VerifyPaper plugin;

    public VerifyPluginMessageListener(VerifyPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(PlayerIdentity.CHANNEL)) {
            return;
        }

        try {
            String json = new String(message, StandardCharsets.UTF_8);
            PlayerIdentity identity = plugin.getGson().fromJson(json, PlayerIdentity.class);

            if (identity != null) {
                if (!plugin.isCached(player.getUniqueId())) {
                    player.sendMessage(MessageUtils.buildVerificationMessage(identity));
                }
                plugin.cacheIdentity(player.getUniqueId(), identity);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing plugin message from Velocity", e);
        }
    }
}

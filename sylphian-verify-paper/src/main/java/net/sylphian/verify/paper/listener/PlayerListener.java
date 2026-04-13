package net.sylphian.verify.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.sylphian.verify.common.MessageUtils;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.common.VerificationResult;
import net.sylphian.verify.paper.VerifyPaper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;

public class PlayerListener implements Listener {
    private final VerifyPaper plugin;

    public PlayerListener(VerifyPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.getPluginConfig().isProxyMode()) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        try {
            VerificationResult result = plugin.getVerifyManager().checkPlayer(uuid, ip).join();
            if (!result.isAllowed()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, result.getKickMessage());
            } else {
                plugin.cacheIdentity(uuid, result.getIdentity());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error checking verification for " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MessageUtils.buildErrorMessage(plugin.getPluginConfig()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.removeIdentity(uuid);
        if (plugin.getVerifyManager() != null) {
            plugin.getVerifyManager().resetTimeoutStrikes(uuid);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerIdentity playerIdentity = plugin.getIdentity(player.getUniqueId());

        if (playerIdentity == null) {
            return;
        }

        String forumName = playerIdentity.forumUsername();

        event.renderer(((source, sourceDisplayName, message, viewer) -> {
            Component displayName = Component.text(forumName);
            return displayName.append(Component.text(": ")).append(message);
        }));
    }
}

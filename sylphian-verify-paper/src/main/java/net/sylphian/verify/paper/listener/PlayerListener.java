package net.sylphian.verify.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.sylphian.verify.common.MessageUtils;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.common.VerificationResult;
import net.sylphian.verify.paper.VerifyPaper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles player-related events for the Sylphian Verify plugin on Paper servers.
 * Responsible for verification checks, visual identity updates, and scoreboard management.
 */
public class PlayerListener implements Listener {
    private final VerifyPaper plugin;

    /**
     * Constructs a new PlayerListener.
     *
     * @param plugin The VerifyPaper plugin instance.
     */
    public PlayerListener(VerifyPaper plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles the pre-login event to verify players before they join.
     * If the plugin is in proxy mode, this check is skipped as it's handled by Velocity.
     *
     * @param event The pre-login event.
     */
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

    /**
     * Handles the player join event to set the player's scoreboard and trigger visual updates.
     *
     * @param event The player join event.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Scoreboard scoreboard = plugin.getPlayerNamesScoreboard();
        if (scoreboard != null) {
            player.setScoreboard(scoreboard);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                PlayerIdentity identity = plugin.getIdentity(online.getUniqueId());

                if (identity == null) {
                    plugin.getLogger().warning("[Visual] Missing identity for " + online.getName());
                    continue;
                }

                plugin.getVisualManager().updateVisuals(online, identity);
            }
        }, 2L);
    }

    /**
     * Handles the player quit event to clean up cached identity data and scoreboard entries.
     *
     * @param event The player quit event.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.removeIdentity(uuid);

        if (plugin.getVisualManager() != null) {
            plugin.getVisualManager().cleanUpPlayer(player);
        }

        if (plugin.getVerifyManager() != null) {
            plugin.getVerifyManager().resetTimeoutStrikes(uuid);
        }
    }

    /**
     * Handles the asynchronous chat event to format chat messages with forum usernames
     * and add interactive links to forum profiles.
     *
     * @param event The chat event.
     */
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerIdentity playerIdentity = plugin.getIdentity(player.getUniqueId());

        if (playerIdentity == null) return;

        event.renderer(plugin.getVisualManager().getChatRenderer(player, playerIdentity));
    }
}

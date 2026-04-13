package net.sylphian.verify.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.sylphian.verify.common.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VerifyPaper extends JavaPlugin implements Listener, PluginMessageListener {

    private VerifyConfig config;
    private VerifyManager verifyManager;
    private Gson gson;
    private final Map<UUID, PlayerIdentity> verificationResponses = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        Path dataDirectory = getDataFolder().toPath();

        try {
            this.config = VerifyConfig.load(dataDirectory.resolve("config.json"), gson);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not load config, using defaults", e);
            this.config = VerifyConfig.createDefault();
        }

        if (!config.isProxyMode()) {
            this.verifyManager = new VerifyManager(config);
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (config.isProxyMode()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, "sylphian:verify", this);
            getLogger().info("Proxy mode enabled, listening for verification data from Velocity");
        } else {
            startVerificationTask();
        }

        getLogger().info("Plugin initialized successfully");
    }

    private void startVerificationTask() {
        if (config.getVerificationIntervalMinutes() <= 0) {
            getLogger().info("Periodic verification task is disabled (interval set to 0)");
            return;
        }

        long intervalTicks = config.getVerificationIntervalMinutes() * 60L * 20;
        getLogger().info("Scheduling verification task to run every " + config.getVerificationIntervalMinutes() + " minutes");

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                String playerName = player.getName();

                verifyManager.getClient().checkVerification(uuid)
                        .thenAccept(response -> {
                            verifyManager.resetTimeoutStrikes(uuid);

                            if (!response.isAllowed()) {
                                getLogger().info("Player " + playerName + " (" + uuid + ") verification failed: " + response.getReason());

                                Bukkit.getScheduler().runTask(this, () ->
                                        player.kick(MessageUtils.buildReverificationFailureMessage(config))
                                );
                            } else {
                                getLogger().info("Player " + playerName + " (" + uuid + ") re-verified successfully");
                                verificationResponses.put(uuid, PlayerIdentity.from(response, uuid));
                            }
                        })
                        .exceptionally(ex -> {
                            int strikes = verifyManager.incrementTimeoutStrike(uuid);

                            getLogger().warning("Verification API exception for player " + playerName +
                                    " (" + uuid + "), strike " + strikes + "/" + config.getMaxTimeoutStrikes());

                            if (strikes >= config.getMaxTimeoutStrikes()) {
                                Bukkit.getScheduler().runTask(this, () ->
                                        player.kick(MessageUtils.buildReverificationFailureMessage(config))
                                );
                                verifyManager.resetTimeoutStrikes(uuid);
                                getLogger().warning("Player " + playerName + " (" + uuid + ") disconnected due to repeated API timeouts");
                            }

                            return null;
                        });
            }
        }, 0L, intervalTicks);
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (config.isProxyMode()) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        try {
            VerificationResult result = verifyManager.checkPlayer(uuid, ip).join();
            if (!result.isAllowed()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, result.getKickMessage());
            } else {
                verificationResponses.put(uuid, result.getIdentity());
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error checking verification for " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MessageUtils.buildErrorMessage(config));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        verificationResponses.remove(uuid);
        if (verifyManager != null) {
            verifyManager.resetTimeoutStrikes(uuid);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("sylphian:verify")) {
            return;
        }

        try {
            String json = new String(message, StandardCharsets.UTF_8);
            PlayerIdentity identity = gson.fromJson(json, PlayerIdentity.class);

            if (identity != null) {
                verificationResponses.put(player.getUniqueId(), identity);

                getLogger().info("Received verification data for " + player.getName() + ": " + identity.forumUsername());
                // Inform player that they have been verified
                player.sendMessage(MessageUtils.buildVerificationMessage(identity));
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error processing plugin message from Velocity", e);
        }
    }
}

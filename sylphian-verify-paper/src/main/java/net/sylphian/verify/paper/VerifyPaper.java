package net.sylphian.verify.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.sylphian.verify.common.MessageUtils;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.common.VerifyConfig;
import net.sylphian.verify.common.VerifyManager;
import net.sylphian.verify.paper.listener.PlayerListener;
import net.sylphian.verify.paper.listener.VerifyPluginMessageListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VerifyPaper extends JavaPlugin {

    private VerifyConfig config;
    private VerifyManager verifyManager;
    private Gson gson;
    private final Map<UUID, PlayerIdentity> verificationResponses = new ConcurrentHashMap<>();

    public VerifyConfig getPluginConfig() {
        return config;
    }

    public VerifyManager getVerifyManager() {
        return verifyManager;
    }

    public Gson getGson() {
        return gson;
    }

    public void cacheIdentity(UUID uuid, PlayerIdentity identity) {
        verificationResponses.put(uuid, identity);
    }

    public void removeIdentity(UUID uuid) {
        verificationResponses.remove(uuid);
    }

    public boolean isCached(UUID uuid) {
        return verificationResponses.containsKey(uuid);
    }

    public PlayerIdentity getIdentity(UUID uuid) {
        return verificationResponses.get(uuid);
    }

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

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        if (config.isProxyMode()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, PlayerIdentity.CHANNEL, new VerifyPluginMessageListener(this));
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
}

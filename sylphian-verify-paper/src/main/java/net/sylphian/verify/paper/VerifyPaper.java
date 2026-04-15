package net.sylphian.verify.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.common.VerifyConfig;
import net.sylphian.verify.common.VerifyManager;
import net.sylphian.verify.paper.listener.PlayerListener;
import net.sylphian.verify.paper.listener.VerifyPluginMessageListener;
import net.sylphian.verify.paper.util.VisualManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;

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
    private Scoreboard scoreboard;
    private final Map<UUID, PlayerIdentity> verificationResponses = new ConcurrentHashMap<>();
    private PlayerListener playerListener;
    private VisualManager visualManager;

    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    public VisualManager getVisualManager() {
        return visualManager;
    }

    public Scoreboard getPlayerNamesScoreboard() {
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        }
        return scoreboard;
    }

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

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline() && visualManager != null) {
            Bukkit.getScheduler().runTask(this, () -> visualManager.updateVisuals(player, identity));
        }
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

        Bukkit.getScoreboardManager();
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(this.scoreboard);
        }

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

        this.visualManager = new VisualManager(this);
        this.playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);

        if (config.isProxyMode()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, PlayerIdentity.CHANNEL, new VerifyPluginMessageListener(this));
            getLogger().info("Proxy mode enabled, listening for verification data from Velocity");
        } else {
            startVerificationTask();
        }

        getLogger().info("Plugin initialised successfully");
    }

    private void startVerificationTask() {
        if (config.getVerificationIntervalMinutes() <= 0) {
            getLogger().info("Periodic verification task is disabled (interval set to 0)");
            return;
        }

        long intervalTicks = config.getVerificationIntervalMinutes() * 60L * 20;
        getLogger().info("Scheduling verification task to run every " + config.getVerificationIntervalMinutes() + " minutes");

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            int playerCount = Bukkit.getOnlinePlayers().size();
            if (playerCount > 0) {
                getLogger().info("Starting periodic verification check for " + playerCount + " players");
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                String playerName = player.getName();

                verifyManager.checkPeriodic(uuid)
                        .thenAccept(result -> {
                            if (!result.isAllowed()) {
                                getLogger().warning("Player " + playerName + " (" + uuid + ") failed periodic verification. Disconnecting.");
                                Bukkit.getScheduler().runTask(this, () ->
                                        player.kick(result.getKickMessage())
                                );
                            } else {
                                int strikes = verifyManager.getStrikeCount(uuid);
                                if (strikes > 0) {
                                    getLogger().warning("Player " + playerName + " (" + uuid + ") has " + strikes + "/" + config.getMaxStrikes() + " strikes");
                                } else if (result.getIdentity() != null) {
                                    getLogger().log(Level.FINE, "Player " + playerName + " (" + uuid + ") re-verified successfully");
                                    cacheIdentity(uuid, result.getIdentity());
                                }
                            }
                        });
            }
        }, 0L, intervalTicks);
    }
}

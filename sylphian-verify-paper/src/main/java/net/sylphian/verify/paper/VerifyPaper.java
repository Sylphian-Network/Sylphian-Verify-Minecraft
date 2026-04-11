package net.sylphian.verify.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.sylphian.verify.common.MessageUtils;
import net.sylphian.verify.common.VerificationResult;
import net.sylphian.verify.common.VerifyConfig;
import net.sylphian.verify.common.VerifyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

public final class VerifyPaper extends JavaPlugin implements Listener {

    private VerifyConfig config;
    private VerifyManager verifyManager;

    @Override
    public void onEnable() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path dataDirectory = getDataFolder().toPath();

        try {
            this.config = VerifyConfig.load(dataDirectory.resolve("config.json"), gson);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not load config, using defaults", e);
            this.config = new VerifyConfig();
        }

        this.verifyManager = new VerifyManager(config);

        getServer().getPluginManager().registerEvents(this, this);

        startVerificationTask();

        getLogger().info("Plugin initialized successfully");
    }

    private void startVerificationTask() {
        long intervalTicks = config.getVerificationIntervalMinutes() * 60L * 20;
        getLogger().info("Scheduling verification task to run every " + config.getVerificationIntervalMinutes() + " minutes");

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                verifyManager.getClient().checkVerification(uuid)
                        .thenAccept(response -> {
                            verifyManager.getTimeoutStrikes().remove(uuid);

                            if (!response.isAllowed()) {
                                getLogger().info("Player " + player.getName() + " (" + uuid + ") verification failed: " + response.getReason());

                                Bukkit.getScheduler().runTask(this, () ->
                                        player.kick(MessageUtils.buildReverificationFailureMessage(config))
                                );
                            } else {
                                getLogger().log(Level.FINE, "Player " + player.getName() + " (" + uuid + ") verified successfully");
                            }
                        })
                        .exceptionally(ex -> {
                            int strikes = verifyManager.getTimeoutStrikes().getOrDefault(uuid, 0) + 1;
                            verifyManager.getTimeoutStrikes().put(uuid, strikes);

                            getLogger().warning("Verification API exception for player " + player.getName() +
                                    " (" + uuid + "), strike " + strikes + "/" + config.getMaxTimeoutStrikes());

                            if (strikes >= config.getMaxTimeoutStrikes()) {
                                Bukkit.getScheduler().runTask(this, () ->
                                        player.kick(MessageUtils.buildReverificationFailureMessage(config))
                                );
                                verifyManager.getTimeoutStrikes().remove(uuid);
                                getLogger().warning("Player " + player.getName() + " (" + uuid + ") disconnected due to repeated API timeouts");
                            }

                            return null;
                        });
            }
        }, 0L, intervalTicks);
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        try {
            VerificationResult result = verifyManager.checkPlayer(uuid, ip).join();
            if (!result.isAllowed()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, result.getKickMessage());
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error checking verification for " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MessageUtils.buildErrorMessage(config));
        }
    }
}

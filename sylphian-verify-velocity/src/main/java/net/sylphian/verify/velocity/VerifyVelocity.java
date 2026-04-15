package net.sylphian.verify.velocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.sylphian.verify.common.PlayerIdentity;
import net.sylphian.verify.common.VerifyConfig;
import net.sylphian.verify.common.VerifyManager;
import net.sylphian.verify.velocity.listener.PlayerListener;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "sylphian-verify",
        name = "Sylphian-Verify",
        version = BuildConstants.VERSION,
        url = "https://sylphian.net",
        authors = {"QuackieMackie"}
)
public class VerifyVelocity {
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from(PlayerIdentity.CHANNEL);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Gson gson;
    private final Map<UUID, PlayerIdentity> verifiedPlayers = new ConcurrentHashMap<>();
    private VerifyConfig config;
    private VerifyManager verifyManager;

    @Inject
    public VerifyVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            this.config = VerifyConfig.load(dataDirectory.resolve("config.json"), gson);
        } catch (IOException e) {
            logger.error("Could not load config, using defaults", e);
            this.config = VerifyConfig.createDefault();
        }

        this.verifyManager = new VerifyManager(config);

        proxy.getChannelRegistrar().register(IDENTIFIER);
        proxy.getEventManager().register(this, new PlayerListener(this, verifiedPlayers));

        startVerificationTask();

        logger.info("Plugin initialised successfully");
    }

    private void startVerificationTask() {
        if (config.getVerificationIntervalMinutes() <= 0) {
            logger.info("Periodic verification task is disabled (interval set to 0)");
            return;
        }

        logger.info("Scheduling verification task to run every {} minutes", config.getVerificationIntervalMinutes());

        proxy.getScheduler().buildTask(this, () -> {
                    if (!proxy.getAllPlayers().isEmpty()) {
                        logger.info("Starting periodic verification check for {} players", proxy.getPlayerCount());
                    }

                    for (Player player : proxy.getAllPlayers()) {
                        UUID uuid = player.getUniqueId();

                        verifyManager.checkPeriodic(uuid)
                                .thenAccept(result -> {
                                    if (!result.isAllowed()) {
                                        logger.warn("Player {} ({}) failed periodic verification. Disconnecting.", player.getUsername(), uuid);
                                        verifiedPlayers.remove(uuid);
                                        player.disconnect(result.getKickMessage());
                                    } else {
                                        int strikes = verifyManager.getStrikeCount(uuid);
                                        if (strikes > 0) {
                                            logger.warn("Player {} ({}) has {}/{} strikes", player.getUsername(), uuid, strikes, config.getMaxStrikes());
                                        } else if (result.getIdentity() != null) {
                                            logger.debug("Player {} ({}) re-verified successfully", player.getUsername(), uuid);
                                            verifiedPlayers.put(uuid, result.getIdentity());
                                            sendVerificationData(player, result.getIdentity());
                                        }
                                    }
                                });
                    }
                })
                .delay(0, TimeUnit.SECONDS)
                .repeat(config.getVerificationIntervalMinutes(), TimeUnit.MINUTES)
                .schedule();
    }


    public void sendVerificationData(Player player, PlayerIdentity identity) {
        player.getCurrentServer().ifPresent(server -> {
            String json = gson.toJson(identity);
            server.sendPluginMessage(IDENTIFIER, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
    }

    public VerifyManager getVerifyManager() {
        return verifyManager;
    }

    public VerifyConfig getConfig() {
        return config;
    }

    public Logger getLogger() {
        return logger;
    }
}

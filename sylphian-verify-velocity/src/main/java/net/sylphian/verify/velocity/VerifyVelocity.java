package net.sylphian.verify.velocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.sylphian.verify.common.*;
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
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("sylphian:verify");

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

                        verifyManager.getClient().checkVerification(uuid)
                                .thenAccept(response -> {
                                    verifyManager.resetTimeoutStrikes(uuid);

                                    if (!response.isAllowed()) {
                                        logger.info("Player {} ({}) verification failed: {}", player.getUsername(), uuid, response.getReason());
                                        verifiedPlayers.remove(uuid);
                                        player.disconnect(MessageUtils.buildReverificationFailureMessage(config));
                                    } else {
                                        logger.debug("Player {} ({}) re-verified successfully", player.getUsername(), uuid);
                                        PlayerIdentity identity = PlayerIdentity.from(response, uuid);
                                        verifiedPlayers.put(uuid, identity);
                                        sendVerificationData(player, identity);
                                    }
                                })
                                .exceptionally(ex -> {
                                    int strikes = verifyManager.incrementTimeoutStrike(uuid);

                                    logger.warn("Verification API exception for player {} ({}), strike {}/{}",
                                            player.getUsername(), uuid, strikes, config.getMaxTimeoutStrikes());

                                    if (strikes >= config.getMaxTimeoutStrikes()) {
                                        player.disconnect(MessageUtils.buildReverificationFailureMessage(config));
                                        verifyManager.resetTimeoutStrikes(uuid);
                                        logger.warn("Player {} ({}) disconnected due to repeated API timeouts", player.getUsername(), uuid);
                                    }

                                    return null;
                                });
                    }
                })
                .delay(0, TimeUnit.SECONDS)
                .repeat(config.getVerificationIntervalMinutes(), TimeUnit.MINUTES)
                .schedule();
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        return EventTask.async(() -> {
            try {
                VerificationResult result = verifyManager.checkPlayer(uuid, ip).join();
                if (!result.isAllowed()) {
                    event.setResult(LoginEvent.ComponentResult.denied(result.getKickMessage()));
                } else {
                    verifiedPlayers.put(uuid, result.getIdentity());
                }
            } catch (Exception e) {
                logger.error("Error checking verification for {}", player.getUsername(), e);
                event.setResult(LoginEvent.ComponentResult.denied(MessageUtils.buildErrorMessage(config)));
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        verifiedPlayers.remove(uuid);
        verifyManager.resetTimeoutStrikes(uuid);
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        PlayerIdentity identity = verifiedPlayers.get(player.getUniqueId());
        if (identity != null) {
            sendVerificationData(player, identity);
        }
    }

    private void sendVerificationData(Player player, PlayerIdentity identity) {
        player.getCurrentServer().ifPresent(server -> {
            String json = gson.toJson(identity);
            server.sendPluginMessage(IDENTIFIER, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
    }
}

package net.sylphian.verify.velocity;
 
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.sylphian.verify.common.MessageUtils;
import net.sylphian.verify.common.VerificationResult;
import net.sylphian.verify.common.VerifyConfig;
import net.sylphian.verify.common.VerifyManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
 
@Plugin(
        id = "sylphian-verify",
        name = "Sylphian-Verify",
        version = BuildConstants.VERSION,
        url = "https://sylphian.net",
        authors = {"QuackieMackie"}
)
public class VerifyVelocity {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Gson gson;
    private VerifyConfig config;
    private VerifyManager verifyManager;

    @Inject
    public VerifyVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
 
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            this.config = VerifyConfig.load(dataDirectory.resolve("config.json"), gson);
        } catch (IOException e) {
            logger.error("Could not load config, using defaults", e);
            this.config = new VerifyConfig();
        }

        this.verifyManager = new VerifyManager(config);

        startVerificationTask();

        logger.info("Plugin initialised successfully");
    }
 
    private void startVerificationTask() {
        logger.info("Scheduling verification task to run every {} minutes", config.getVerificationIntervalMinutes());

        proxy.getScheduler().buildTask(this, () -> {
                    for (Player player : proxy.getAllPlayers()) {
                        UUID uuid = player.getUniqueId();

                        verifyManager.getClient().checkVerification(uuid)
                                .thenAccept(response -> {
                                    verifyManager.getTimeoutStrikes().remove(uuid);

                                    if (!response.isAllowed()) {
                                        logger.info("Player {} ({}) verification failed: {}", player.getUsername(), uuid, response.getReason());
                                        player.disconnect(MessageUtils.buildReverificationFailureMessage(config));
                                    } else {
                                        logger.debug("Player {} ({}) verified successfully", player.getUsername(), uuid);
                                    }
                                })
                                .exceptionally(ex -> {
                                    int strikes = verifyManager.getTimeoutStrikes().getOrDefault(uuid, 0) + 1;
                                    verifyManager.getTimeoutStrikes().put(uuid, strikes);

                                    logger.warn("Verification API exception for player {} ({}), strike {}/{}",
                                            player.getUsername(), uuid, strikes, config.getMaxTimeoutStrikes());

                                    if (strikes >= config.getMaxTimeoutStrikes()) {
                                        player.disconnect(MessageUtils.buildReverificationFailureMessage(config));
                                        verifyManager.getTimeoutStrikes().remove(uuid);
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
                }
            } catch (Exception e) {
                logger.error("Error checking verification for {}", player.getUsername(), e);
                event.setResult(LoginEvent.ComponentResult.denied(MessageUtils.buildErrorMessage(config)));
            }
        });
    }
}

package net.sylphian.verify.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class VerifyManager {
    private final VerifyClient client;
    private final VerifyConfig config;

    private final Cache<UUID, Integer> uuidAttempts;
    private final Cache<String, Integer> ipAttempts;
    private final Cache<UUID, Long> uuidCooldown;
    private final Cache<String, Long> ipCooldown;

    private final Map<UUID, Integer> timeoutStrikes = new ConcurrentHashMap<>();

    public VerifyManager(VerifyConfig config) {
        this.config = config;
        this.client = new VerifyClient(config.getApiUrl(), config.getApiKey(), config.getApiTimeoutSeconds());

        this.uuidAttempts = Caffeine.newBuilder()
                .expireAfterWrite(config.getAttemptExpiryMinutes(), TimeUnit.MINUTES)
                .build();
        this.ipAttempts = Caffeine.newBuilder()
                .expireAfterWrite(config.getAttemptExpiryMinutes(), TimeUnit.MINUTES)
                .build();
        this.uuidCooldown = Caffeine.newBuilder()
                .expireAfterWrite(config.getCooldownMinutes(), TimeUnit.MINUTES)
                .build();
        this.ipCooldown = Caffeine.newBuilder()
                .expireAfterWrite(config.getCooldownMinutes(), TimeUnit.MINUTES)
                .build();
    }

    public CompletableFuture<VerificationResult> checkPlayer(UUID uuid, String ip) {
        Long uuidExpiry = uuidCooldown.getIfPresent(uuid);
        if (uuidExpiry != null) {
            return CompletableFuture.completedFuture(
                    VerificationResult.denied(MessageUtils.buildCooldownMessage(uuidExpiry, config), null)
            );
        }

        Long ipExpiry = ipCooldown.getIfPresent(ip);
        if (ipExpiry != null) {
            return CompletableFuture.completedFuture(
                    VerificationResult.denied(MessageUtils.buildCooldownMessage(ipExpiry, config), null)
            );
        }

        return client.checkVerification(uuid)
                .thenApply(response -> {
                    if (!response.isAllowed()) {
                        handleFailedAttempt(uuid, ip);
                        return VerificationResult.denied(MessageUtils.buildKickMessage(response, config), response);
                    }
                    return VerificationResult.allowed();
                });
    }

    private void handleFailedAttempt(UUID uuid, String ip) {
        // UUID attempts
        int uAttempts = uuidAttempts.get(uuid, k -> 0) + 1;
        if (uAttempts >= config.getUuidAttemptLimit()) {
            long expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(config.getCooldownMinutes());
            uuidCooldown.put(uuid, expiry);
            uuidAttempts.invalidate(uuid);
        } else {
            uuidAttempts.put(uuid, uAttempts);
        }

        // IP attempts
        int iAttempts = ipAttempts.get(ip, k -> 0) + 1;
        if (iAttempts >= config.getIpAttemptLimit()) {
            long expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(config.getCooldownMinutes());
            ipCooldown.put(ip, expiry);
            ipAttempts.invalidate(ip);
        } else {
            ipAttempts.put(ip, iAttempts);
        }
    }

    public Map<UUID, Integer> getTimeoutStrikes() {
        return timeoutStrikes;
    }

    public VerifyClient getClient() {
        return client;
    }
}

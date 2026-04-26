package net.sylphian.verify.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.sylphian.verify.api.VerifyClient;
import net.sylphian.verify.api.model.VerificationResponse;
 
import java.util.Collection;
import java.util.HashMap;
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

    private final Map<UUID, Integer> strikes = new ConcurrentHashMap<>();

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
                    PlayerIdentity identity = PlayerIdentity.from(response, uuid);
                    if (!response.isAllowed()) {
                        handleFailedAttempt(uuid, ip);
                        return VerificationResult.denied(MessageUtils.buildKickMessage(response, config), identity);
                    }
                    return VerificationResult.allowed(identity);
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

    public CompletableFuture<Map<UUID, VerificationResult>> checkPeriodicBatch(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        return client.checkVerificationBatch(uuids)
                .handle((responses, ex) -> {
                    Map<UUID, VerificationResult> results = new HashMap<>();
                    if (ex != null) {
                        for (UUID uuid : uuids) {
                            if (config.isStrikeOnApiFailure()) {
                                int count = incrementStrike(uuid);
                                if (count >= config.getMaxStrikes()) {
                                    resetStrikes(uuid);
                                    results.put(uuid, VerificationResult.denied(MessageUtils.buildReverificationFailureMessage(config), null));
                                    continue;
                                }
                            }
                            results.put(uuid, VerificationResult.allowed(null));
                        }
                        return results;
                    }

                    for (UUID uuid : uuids) {
                        VerificationResponse response = responses.get(uuid.toString());
                        if (response == null) {
                            results.put(uuid, VerificationResult.allowed(null));
                            continue;
                        }

                        PlayerIdentity identity = PlayerIdentity.from(response, uuid);
                        if (response.isAllowed()) {
                            resetStrikes(uuid);
                            results.put(uuid, VerificationResult.allowed(identity));
                        } else {
                            int count = incrementStrike(uuid);
                            if (count >= config.getMaxStrikes()) {
                                resetStrikes(uuid);
                                results.put(uuid, VerificationResult.denied(MessageUtils.buildReverificationFailureMessage(config), identity));
                            } else {
                                results.put(uuid, VerificationResult.allowed(identity));
                            }
                        }
                    }
                    return results;
                });
    }

    public int incrementStrike(UUID uuid) {
        return strikes.compute(uuid, (k, v) -> v == null ? 1 : v + 1);
    }

    public void resetStrikes(UUID uuid) {
        strikes.remove(uuid);
    }

    public int getStrikeCount(UUID uuid) {
        return strikes.getOrDefault(uuid, 0);
    }

    public Map<UUID, Integer> getStrikes() {
        return Map.copyOf(strikes);
    }

    public VerifyClient getClient() {
        return client;
    }
}

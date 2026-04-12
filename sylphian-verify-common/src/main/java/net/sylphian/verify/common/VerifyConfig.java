package net.sylphian.verify.common;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class VerifyConfig {
    private static final String DEFAULT_API_URL = "http://www.example.com/api/verify/minecraft";
    private static final String DEFAULT_API_KEY = "";
    private static final int DEFAULT_VERIFICATION_INTERVAL_MINUTES = 5;
    private static final int DEFAULT_MAX_TIMEOUT_STRIKES = 3;
    private static final int DEFAULT_UUID_ATTEMPT_LIMIT = 5;
    private static final int DEFAULT_IP_ATTEMPT_LIMIT = 10;
    private static final int DEFAULT_COOLDOWN_MINUTES = 10;
    private static final int DEFAULT_ATTEMPT_EXPIRY_MINUTES = 5;
    private static final int DEFAULT_API_TIMEOUT_SECONDS = 10;

    private String apiUrl;
    private String apiKey;

    private Integer verificationIntervalMinutes;
    private Integer maxTimeoutStrikes;
    private Integer uuidAttemptLimit;
    private Integer ipAttemptLimit;
    private Integer cooldownMinutes;
    private Integer attemptExpiryMinutes;
    private Integer apiTimeoutSeconds;

    private Map<String, String> apiResponses;

    public static VerifyConfig createDefault() {
        VerifyConfig config = new VerifyConfig();
        config.ensureDefaults();
        return config;
    }

    private static Map<String, String> createDefaultApiResponses() {
        Map<String, String> map = new HashMap<>();
        map.put("UUID not linked to any forum account", "Your account has not been added to the forum, please add your account before attempting to join again.");
        map.put("Account not confirmed", "Your forum account is linked but not confirmed. Please use the passcode below.");
        map.put("Brute Force Cooldown", "Too many failed attempts. Please try again in {time} minutes.");
        map.put("Re-verification failed", "Your account is no longer verified. This could be because your account is no longer linked or an API error occurred. Please ensure your account is linked and check our website for status updates.");
        map.put("Verification API Error", "An error occurred while checking the API.");
        return map;
    }

    public boolean ensureDefaults() {
        boolean modified = false;
        if (apiUrl == null) {
            apiUrl = DEFAULT_API_URL;
            modified = true;
        }
        if (apiKey == null) {
            apiKey = DEFAULT_API_KEY;
            modified = true;
        }

        if (verificationIntervalMinutes == null) {
            verificationIntervalMinutes = DEFAULT_VERIFICATION_INTERVAL_MINUTES;
            modified = true;
        }
        if (maxTimeoutStrikes == null) {
            maxTimeoutStrikes = DEFAULT_MAX_TIMEOUT_STRIKES;
            modified = true;
        }
        if (uuidAttemptLimit == null) {
            uuidAttemptLimit = DEFAULT_UUID_ATTEMPT_LIMIT;
            modified = true;
        }
        if (ipAttemptLimit == null) {
            ipAttemptLimit = DEFAULT_IP_ATTEMPT_LIMIT;
            modified = true;
        }
        if (cooldownMinutes == null) {
            cooldownMinutes = DEFAULT_COOLDOWN_MINUTES;
            modified = true;
        }
        if (attemptExpiryMinutes == null) {
            attemptExpiryMinutes = DEFAULT_ATTEMPT_EXPIRY_MINUTES;
            modified = true;
        }
        if (apiTimeoutSeconds == null) {
            apiTimeoutSeconds = DEFAULT_API_TIMEOUT_SECONDS;
            modified = true;
        }

        if (apiResponses == null) {
            apiResponses = createDefaultApiResponses();
            modified = true;
        } else {
            Map<String, String> defaults = createDefaultApiResponses();
            for (Map.Entry<String, String> entry : defaults.entrySet()) {
                if (apiResponses.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                    modified = true;
                }
            }
        }
        return modified;
    }

    public String getApiUrl() { return apiUrl; }
    public String getApiKey() { return apiKey; }
    public int getVerificationIntervalMinutes() { return verificationIntervalMinutes; }
    public int getMaxTimeoutStrikes() { return maxTimeoutStrikes; }
    public int getUuidAttemptLimit() { return uuidAttemptLimit; }
    public int getIpAttemptLimit() { return ipAttemptLimit; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public int getAttemptExpiryMinutes() { return attemptExpiryMinutes; }
    public int getApiTimeoutSeconds() { return apiTimeoutSeconds; }
    public Map<String, String> getApiResponses() { return apiResponses; }

    public static VerifyConfig load(Path path, Gson gson) throws IOException {
        VerifyConfig config;
        boolean modified;
        if (Files.notExists(path)) {
            config = new VerifyConfig();
            config.ensureDefaults();
            modified = true;
        } else {
            try (Reader reader = Files.newBufferedReader(path)) {
                config = gson.fromJson(reader, VerifyConfig.class);
            } catch (com.google.gson.JsonParseException e) {
                throw new IOException("Malformed configuration file: " + path, e);
            }
            if (config == null) {
                config = new VerifyConfig();
                config.ensureDefaults();
                modified = true;
            } else {
                modified = config.ensureDefaults();
            }
        }
        if (modified) {
            save(path, config, gson);
        }
        return config;
    }

    public static void save(Path path, VerifyConfig config, Gson gson) throws IOException {
        Path parent = path.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            gson.toJson(config, writer);
        }
    }
}

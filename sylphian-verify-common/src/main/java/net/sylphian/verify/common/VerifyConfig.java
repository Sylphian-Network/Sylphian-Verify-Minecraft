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
    private final String apiUrl = "http://www.example.com/api/verify/minecraft";
    private final String apiKey = "";

    private final int verificationIntervalMinutes = 5;
    private final int maxTimeoutStrikes = 3;
    private final int uuidAttemptLimit = 5;
    private final int ipAttemptLimit = 10;
    private final int cooldownMinutes = 10;
    private final int attemptExpiryMinutes = 5;
    private final int apiTimeoutSeconds = 10;

    private final Map<String, String> apiResponses = new HashMap<>() {{
        put("UUID not linked to any forum account", "Your account has not been added to the forum, please add your account before attempting to join again.");
        put("Account not confirmed", "Your forum account is linked but not confirmed. Please use the passcode below.");
        put("Brute Force Cooldown", "Too many failed attempts. Please try again in {time} minutes.");
        put("Re-verification failed", "Your account is no longer verified. This could be because your account is no longer linked or an API error occurred. Please ensure your account is linked and check our website for status updates.");
        put("Verification API Error", "An error occurred while checking the API.");
    }};

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
        if (Files.notExists(path)) {
            VerifyConfig config = new VerifyConfig();
            save(path, config, gson);
            return config;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return gson.fromJson(reader, VerifyConfig.class);
        }
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

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
    private String apiUrl = "http://www.example.com/api/verify/minecraft";
    private String apiKey = "";

    private int verificationIntervalMinutes = 5;
    private int maxTimeoutStrikes = 3;
    private int uuidAttemptLimit = 5;
    private int ipAttemptLimit = 10;
    private int cooldownMinutes = 10;
    private int attemptExpiryMinutes = 5;
    private int apiTimeoutSeconds = 10;

    private Map<String, String> apiResponses = createDefaultApiResponses();

    private static Map<String, String> createDefaultApiResponses() {
        Map<String, String> map = new HashMap<>();
        map.put("UUID not linked to any forum account", "Your account has not been added to the forum, please add your account before attempting to join again.");
        map.put("Account not confirmed", "Your forum account is linked but not confirmed. Please use the passcode below.");
        map.put("Brute Force Cooldown", "Too many failed attempts. Please try again in {time} minutes.");
        map.put("Re-verification failed", "Your account is no longer verified. This could be because your account is no longer linked or an API error occurred. Please ensure your account is linked and check our website for status updates.");
        map.put("Verification API Error", "An error occurred while checking the API.");
        return map;
    }

    public void ensureDefaults() {
        if (apiUrl == null) apiUrl = "http://www.example.com/api/verify/minecraft";
        if (apiKey == null) apiKey = "";
        if (apiResponses == null) {
            apiResponses = createDefaultApiResponses();
        } else {
            createDefaultApiResponses().forEach(apiResponses::putIfAbsent);
        }
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
        if (Files.notExists(path)) {
            config = new VerifyConfig();
        } else {
            try (Reader reader = Files.newBufferedReader(path)) {
                config = gson.fromJson(reader, VerifyConfig.class);
            } catch (com.google.gson.JsonParseException e) {
                throw new IOException("Malformed configuration file: " + path, e);
            }
            if (config == null) {
                config = new VerifyConfig();
            } else {
                config.ensureDefaults();
            }
        }
        save(path, config, gson);
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

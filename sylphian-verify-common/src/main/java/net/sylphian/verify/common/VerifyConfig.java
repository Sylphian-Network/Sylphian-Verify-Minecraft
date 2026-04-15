package net.sylphian.verify.common;

import com.google.gson.Gson;
import net.sylphian.verify.api.model.VerificationReason;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class VerifyConfig {
    private static final String DEFAULT_API_KEY = "";
    private static final int DEFAULT_VERIFICATION_INTERVAL_MINUTES = 5;
    private static final int DEFAULT_MAX_STRIKES = 3;
    private static final int DEFAULT_UUID_ATTEMPT_LIMIT = 5;
    private static final int DEFAULT_IP_ATTEMPT_LIMIT = 10;
    private static final int DEFAULT_COOLDOWN_MINUTES = 10;
    private static final int DEFAULT_ATTEMPT_EXPIRY_MINUTES = 5;
    private static final int DEFAULT_API_TIMEOUT_SECONDS = 10;
    private static final boolean DEFAULT_PROXY_MODE = false;
    private static final boolean DEFAULT_STRIKE_ON_API_FAILURE = true;
    private static final String DEFAULT_FORUM_BASE_URL = "https://example.com/community";

    private static final String API_PATH = "/api/verify/minecraft";

    /**
     * API Key for authorization with the XenForo/Verification API.
     */
    private String apiKey;

    /**
     * Frequency of re-verification checks for online players in minutes.
     * Set to 0 to disable periodic checks.
     */
    private Integer verificationIntervalMinutes;

    /**
     * Number of consecutive API timeouts before a player is disconnected.
     */
    private Integer maxStrikes;

    /**
     * Maximum number of failed verification attempts by UUID before a cooldown is triggered.
     */
    private Integer uuidAttemptLimit;

    /**
     * Maximum number of failed verification attempts by IP address before a cooldown is triggered.
     */
    private Integer ipAttemptLimit;

    /**
     * Duration of the brute-force cooldown in minutes.
     */
    private Integer cooldownMinutes;

    /**
     * Duration before a single failed attempt strike expires in minutes.
     */
    private Integer attemptExpiryMinutes;

    /**
     * Timeout for API requests in seconds.
     */
    private Integer apiTimeoutSeconds;

    /**
     * Whether the plugin is running in a proxy network (Velocity + Paper).
     * If true, the Paper plugin will listen for verification data from Velocity instead of calling the API directly.
     * On Velocity, this should be false as it will always call the API.
     */
    private Boolean proxyMode;

    /**
     * Whether to count API timeouts/failures as strikes during periodic verification.
     */
    private Boolean strikeOnApiFailure;

    /**
     * Base URL for the community/forum for profile links.
     */
    private String forumBaseUrl;

    /**
     * Customizable kick messages for various API response reasons.
     * Key is the reason identifier from the API, value is the message displayed to the player.
     */
    private Map<VerificationReason, String> apiResponses;

    public static VerifyConfig createDefault() {
        VerifyConfig config = new VerifyConfig();
        config.ensureDefaults();
        return config;
    }

    private static Map<VerificationReason, String> createDefaultApiResponses() {
        Map<VerificationReason, String> map = new HashMap<>();
        map.put(VerificationReason.UUID_NOT_LINKED, "Your account has not been added to the forum, please add your account before attempting to join again.");
        map.put(VerificationReason.ACCOUNT_NOT_CONFIRMED, "Your forum account is linked but not confirmed. Please use the passcode below.");
        map.put(VerificationReason.BRUTE_FORCE_BLOCKED, "Too many failed attempts. Please try again in {time} minutes.");
        map.put(VerificationReason.RE_VERIFICATION_FAILED, "Your account is no longer verified. This could be because your account is no longer linked or an API error occurred. Please ensure your account is linked and check our website for status updates.");
        map.put(VerificationReason.API_ERROR, "An error occurred while checking the API.");
        map.put(VerificationReason.API_SUCCESS_NO_DATA, "API returned success but no verification data.");
        map.put(VerificationReason.API_FAILURE_NO_MESSAGE, "API reported failure without message.");
        return map;
    }

    public boolean ensureDefaults() {
        boolean modified = false;
        if (apiKey == null) {
            apiKey = DEFAULT_API_KEY;
            modified = true;
        }
        if (verificationIntervalMinutes == null) {
            verificationIntervalMinutes = DEFAULT_VERIFICATION_INTERVAL_MINUTES;
            modified = true;
        }
        if (maxStrikes == null) {
            maxStrikes = DEFAULT_MAX_STRIKES;
            modified = true;
        }
        if (strikeOnApiFailure == null) {
            strikeOnApiFailure = DEFAULT_STRIKE_ON_API_FAILURE;
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
        if (proxyMode == null) {
            proxyMode = DEFAULT_PROXY_MODE;
            modified = true;
        }
        if (forumBaseUrl == null) {
            forumBaseUrl = DEFAULT_FORUM_BASE_URL;
            modified = true;
        }

        if (apiResponses == null) {
            apiResponses = createDefaultApiResponses();
            modified = true;
        } else {
            Map<VerificationReason, String> defaults = createDefaultApiResponses();
            for (Map.Entry<VerificationReason, String> entry : defaults.entrySet()) {
                if (apiResponses.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                    modified = true;
                }
            }
        }
        return modified;
    }

    public String getApiUrl() {
        return URI.create(forumBaseUrl).resolve(API_PATH).toString();
    }
    public String getApiKey() { return apiKey; }
    public int getVerificationIntervalMinutes() { return verificationIntervalMinutes; }
    public int getMaxStrikes() { return maxStrikes; }
    public boolean isStrikeOnApiFailure() { return strikeOnApiFailure; }
    public int getUuidAttemptLimit() { return uuidAttemptLimit; }
    public int getIpAttemptLimit() { return ipAttemptLimit; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public int getAttemptExpiryMinutes() { return attemptExpiryMinutes; }
    public int getApiTimeoutSeconds() { return apiTimeoutSeconds; }
    public boolean isProxyMode() { return proxyMode; }
    public String getForumBaseUrl() { return forumBaseUrl; }
    public Map<VerificationReason, String> getApiResponses() { return apiResponses; }

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

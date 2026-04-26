package net.sylphian.verify.api;
 
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.sylphian.verify.api.model.ApiEnvelope;
import net.sylphian.verify.api.model.VerificationReason;
import net.sylphian.verify.api.model.VerificationResponse;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VerifyClient implements VerifyService {
    private final String apiUrl;
    private final String apiKey;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final Gson gson;
    private final Type responseType = new TypeToken<ApiEnvelope<VerificationResponse>>(){}.getType();
    private final Type batchResponseType = new TypeToken<ApiEnvelope<Map<String, VerificationResponse>>>(){}.getType();
 
    public VerifyClient(String apiUrl, String apiKey, int timeoutSeconds) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.gson = new Gson();
    }

    @Override
    public CompletableFuture<VerificationResponse> checkVerification(UUID uuid) {
        String url = apiUrl + "?uuid=" + uuid.toString();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "VerifyPlugin/1.0")
                .header("XF-Api-Key", apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body();
                    int code = response.statusCode();

                    if (code == 200 || code == 400 || code == 403 || code == 404) {
                        try {
                            ApiEnvelope<VerificationResponse> envelope = gson.fromJson(body, responseType);
                            if (envelope == null) throw new RuntimeException("API returned an empty response body");

                            if (envelope.isSuccess()) {
                                VerificationResponse data = envelope.getData();
                                if (data == null) {
                                    return new VerificationResponse(false, VerificationReason.API_SUCCESS_NO_DATA);
                                }
                                if (data.getAllowed() == null) {
                                    data.setAllowed(false);
                                }
                                return data;
                            } else {
                                return new VerificationResponse(false, VerificationReason.API_FAILURE_NO_MESSAGE);
                            }
                        } catch (com.google.gson.JsonSyntaxException e) {
                            System.err.println("[Verify] Failed to parse API response. Status: " + code);
                            System.err.println("[Verify] Response body: " + body);
                            throw new RuntimeException("API returned invalid JSON. Check console for details.", e);
                        }
                    } else {
                        throw new RuntimeException("Unexpected response code: " + code + " | Body: " + body);
                    }
                });
    }

    @Override
    public CompletableFuture<Map<String, VerificationResponse>> checkVerificationBatch(Collection<UUID> uuids) {
        StringBuilder urlBuilder = new StringBuilder(apiUrl);
        urlBuilder.append("?");
        boolean first = true;
        for (UUID uuid : uuids) {
            if (!first) {
                urlBuilder.append("&");
            }
            urlBuilder.append("uuids[]=").append(uuid.toString());
            first = false;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .header("Accept", "application/json")
                .header("User-Agent", "VerifyPlugin/1.0")
                .header("XF-Api-Key", apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body();
                    int code = response.statusCode();

                    if (code == 200 || code == 400 || code == 403 || code == 404) {
                        try {
                            ApiEnvelope<Map<String, VerificationResponse>> envelope = gson.fromJson(body, batchResponseType);
                            if (envelope == null) throw new RuntimeException("API returned an empty response body");

                            if (envelope.isSuccess()) {
                                Map<String, VerificationResponse> data = envelope.getData();
                                if (data == null) {
                                    return Map.of();
                                }
                                data.values().forEach(v -> {
                                    if (v != null && v.getAllowed() == null) {
                                        v.setAllowed(false);
                                    }
                                });
                                return data;
                            } else {
                                throw new RuntimeException("API reported failure: " + (envelope.getMessage() != null ? envelope.getMessage() : "No message provided"));
                            }
                        } catch (com.google.gson.JsonSyntaxException e) {
                            System.err.println("[Verify] Failed to parse API response. Status: " + code);
                            System.err.println("[Verify] Response body: " + body);
                            throw new RuntimeException("API returned invalid JSON. Check console for details.", e);
                        }
                    } else {
                        throw new RuntimeException("Unexpected response code: " + code + " | Body: " + body);
                    }
                });
    }
}

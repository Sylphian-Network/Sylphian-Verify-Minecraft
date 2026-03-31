package net.sylphian.verify.common;
 
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.sylphian.verify.api.VerifyService;
import net.sylphian.verify.api.model.ApiEnvelope;
import net.sylphian.verify.api.model.VerificationResponse;
 
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
 
public class VerifyClient implements VerifyService {
    private final String apiUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;
    private final Type responseType = new TypeToken<ApiEnvelope<VerificationResponse>>(){}.getType();
 
    public VerifyClient(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.gson = new Gson();
    }
 
    public CompletableFuture<VerificationResponse> checkVerification(UUID uuid) {
        String url = apiUrl + "?uuid=" + uuid.toString();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "VerifyPlugin/1.0")
                .header("XF-Api-Key", apiKey)
                .GET();

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body();
                    int code = response.statusCode();

                    if (code == 200 || code == 400 || code == 403 || code == 404) {
                        try {
                            ApiEnvelope<VerificationResponse> envelope = gson.fromJson(body, responseType);
                            if (envelope == null) throw new RuntimeException("API returned an empty body");

                            if (envelope.isSuccess()) {
                                return envelope.getData();
                            } else {
                                return new VerificationResponse(false, envelope.getMessage());
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

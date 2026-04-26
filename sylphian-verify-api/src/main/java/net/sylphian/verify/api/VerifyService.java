package net.sylphian.verify.api;
 
import net.sylphian.verify.api.model.VerificationResponse;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
 
public interface VerifyService {
    CompletableFuture<VerificationResponse> checkVerification(UUID uuid);
    CompletableFuture<Map<String, VerificationResponse>> checkVerificationBatch(Collection<UUID> uuids);
}

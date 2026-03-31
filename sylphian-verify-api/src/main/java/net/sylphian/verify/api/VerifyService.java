package net.sylphian.verify.api;
 
import net.sylphian.verify.api.model.VerificationResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
 
public interface VerifyService {
    CompletableFuture<VerificationResponse> checkVerification(UUID uuid);
}

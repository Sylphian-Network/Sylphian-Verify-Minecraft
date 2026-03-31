package net.sylphian.verify.common;

import net.kyori.adventure.text.Component;
import net.sylphian.verify.api.model.VerificationResponse;

public class VerificationResult {
    private final boolean allowed;
    private final Component kickMessage;
    private final VerificationResponse response;

    private VerificationResult(boolean allowed, Component kickMessage, VerificationResponse response) {
        this.allowed = allowed;
        this.kickMessage = kickMessage;
        this.response = response;
    }

    public static VerificationResult allowed() {
        return new VerificationResult(true, null, null);
    }

    public static VerificationResult denied(Component kickMessage, VerificationResponse response) {
        return new VerificationResult(false, kickMessage, response);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Component getKickMessage() {
        return kickMessage;
    }

    public VerificationResponse getResponse() {
        return response;
    }
}

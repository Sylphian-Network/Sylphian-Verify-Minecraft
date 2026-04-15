package net.sylphian.verify.common;

import net.kyori.adventure.text.Component;

public class VerificationResult {
    private final boolean allowed;
    private final Component kickMessage;
    private final PlayerIdentity identity;

    private VerificationResult(boolean allowed, Component kickMessage, PlayerIdentity identity) {
        this.allowed = allowed;
        this.kickMessage = kickMessage;
        this.identity = identity;
    }

    public static VerificationResult allowed(PlayerIdentity identity) {
        return new VerificationResult(true, null, identity);
    }

    public static VerificationResult denied(Component kickMessage, PlayerIdentity identity) {
        return new VerificationResult(false, kickMessage, identity);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Component getKickMessage() {
        return kickMessage;
    }

    public PlayerIdentity getIdentity() {
        return identity;
    }
}

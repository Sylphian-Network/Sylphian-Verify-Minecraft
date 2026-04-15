package net.sylphian.verify.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.sylphian.verify.api.model.VerificationReason;
import net.sylphian.verify.api.model.VerificationResponse;

public class MessageUtils {
    public static Component buildKickMessage(VerificationResponse response, VerifyConfig config) {
        VerificationReason reason = response.getReason();

        if (reason == null) {
            reason = VerificationReason.ACCOUNT_NOT_CONFIRMED;
        }

        String displayReason = config.getApiResponses().get(reason);
        if (displayReason == null) {
            displayReason = "Verification failed: " + reason.name();
        }

        Component message = Component.text(displayReason, NamedTextColor.RED, TextDecoration.BOLD);

        if (response.getPasscode() != null && !response.getPasscode().isEmpty()) {
            message = message.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Your verification passcode is: ", NamedTextColor.YELLOW))
                    .append(Component.text(response.getPasscode(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("Please enter this code on the website to link your account.", NamedTextColor.GRAY));
        }

        return message;
    }

    public static Component buildCooldownMessage(long expiryMillis, VerifyConfig config) {
        long remainingMillis = expiryMillis - System.currentTimeMillis();
        long seconds = (remainingMillis / 1000) % 60;
        long minutes = (remainingMillis / (1000 * 60)) % 60;

        String timeLeft = String.format("%d:%02d", minutes, seconds);

        VerificationReason reason = VerificationReason.BRUTE_FORCE_BLOCKED;
        String displayReason = config.getApiResponses().getOrDefault(reason, "Too many failed attempts. Please try again in " + timeLeft + " minutes.");

        if (displayReason.contains("{time}")) {
            displayReason = displayReason.replace("{time}", timeLeft);
        }

        return Component.text(displayReason, NamedTextColor.RED);
    }

    public static Component buildErrorMessage(VerifyConfig config) {
        VerificationReason reason = VerificationReason.API_ERROR;
        String displayReason = config.getApiResponses().getOrDefault(reason, "An error occurred while verifying your account. Please try again later.");
        return Component.text(displayReason, NamedTextColor.RED);
    }

    public static Component buildReverificationFailureMessage(VerifyConfig config) {
        VerificationReason reason = VerificationReason.RE_VERIFICATION_FAILED;
        String displayReason = config.getApiResponses().getOrDefault(reason, "Your account is no longer verified. This could be because your account is no longer linked or an API error occurred. Please ensure your account is linked and check our website for status updates.");
        return Component.text(displayReason, NamedTextColor.RED, TextDecoration.BOLD);
    }

    public static Component buildVerificationMessage(PlayerIdentity identity) {
        return Component.text("Verification successful! ", NamedTextColor.GREEN)
                .append(Component.text("Connected as ", NamedTextColor.GRAY))
                .append(Component.text(identity.forumUsername(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.GRAY));
    }
}

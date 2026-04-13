package net.sylphian.verify.common;

import net.sylphian.verify.api.model.VerificationResponse;

import java.util.UUID;

/**
 * Represents the runtime identity of a player inside the Minecraft network.
 * Derived from raw API data (VerificationResponse).
 */
public record PlayerIdentity(UUID uuid, String forumUsername) {

    public static PlayerIdentity from(VerificationResponse response, UUID uuid) {
        String normalizedUsername = response.getForumUsername() != null ? response.getForumUsername().toLowerCase() : null;
        return new PlayerIdentity(
                uuid,
                normalizedUsername
        );
    }
}

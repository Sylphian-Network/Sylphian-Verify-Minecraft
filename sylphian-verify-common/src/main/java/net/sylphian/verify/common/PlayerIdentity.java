package net.sylphian.verify.common;

import net.sylphian.verify.api.model.VerificationResponse;

import java.util.UUID;

/**
 * Represents the runtime identity of a player inside the Minecraft network.
 * Derived from raw API data (VerificationResponse).
 */
public record PlayerIdentity(UUID uuid, String forumUsername) {
    public static final String CHANNEL = "sylphian:verify";

    public static PlayerIdentity from(VerificationResponse response, UUID uuid) {
        return new PlayerIdentity(
                uuid,
                response.getForumUsername()
        );
    }
}

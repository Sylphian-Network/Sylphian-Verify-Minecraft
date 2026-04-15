package net.sylphian.verify.api.model;

/**
 * Represents the reason for a verification result.
 */
public enum VerificationReason {
    /**
     * The UUID is not linked to any forum account.
     */
    UUID_NOT_LINKED,

    /**
     * The forum account is linked but not confirmed (e.g., waiting for passcode).
     */
    ACCOUNT_NOT_CONFIRMED,

    /**
     * Too many failed verification attempts, brute force protection triggered.
     */
    BRUTE_FORCE_BLOCKED,

    /**
     * Periodic re-verification failed.
     */
    RE_VERIFICATION_FAILED,

    /**
     * A generic API error occurred.
     */
    API_ERROR,

    /**
     * API returned success but no data was provided.
     */
    API_SUCCESS_NO_DATA,

    /**
     * API reported failure without providing a specific message or reason.
     */
    API_FAILURE_NO_MESSAGE
}

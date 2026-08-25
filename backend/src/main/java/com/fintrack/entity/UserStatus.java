package com.fintrack.entity;

/**
 * Lifecycle state of a user account.
 *
 * <p>Only {@link #ACTIVE} accounts may authenticate. The distinction between the
 * two blocked states is intentional: {@link #INACTIVE} is self-service (the user
 * closed or deactivated the account) while {@link #SUSPENDED} is administrative
 * and cannot be lifted by the user.
 */
public enum UserStatus {

    /** Account may authenticate and use the API. */
    ACTIVE,

    /** Account is dormant, typically after self-deactivation. */
    INACTIVE,

    /** Account is blocked by an administrator. */
    SUSPENDED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}

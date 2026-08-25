package com.fintrack.entity;

/**
 * Coarse-grained authority granted to a user account.
 *
 * <p>Roles are a fixed, closed set: FinTrack has no runtime role management, so a
 * normalized {@code roles}/{@code user_roles} pair of tables would add joins and
 * migration surface without buying anything. Should dynamic roles ever be needed,
 * this enum can be replaced by a lookup table without changing any controller,
 * because authorization is expressed against {@link #authority()} rather than
 * against the enum constants directly.
 */
public enum Role {

    /** Full administrative access, including user administration. */
    ADMIN,

    /** Elevated read access across users; cannot administer accounts. */
    MANAGER,

    /** Default role: full control over own data only. */
    USER;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    /**
     * The role names as compile-time constants, for use inside annotations.
     *
     * <p>{@code @PreAuthorize} takes a String literal, and an annotation value must
     * be a compile-time constant -- {@code Role.ADMIN.name()} is not one and will
     * not compile there. Without these constants every guarded method would carry a
     * bare {@code "hasRole('ADMIN')"}, and renaming a role would leave those strings
     * silently pointing at a role that no longer exists.
     *
     * <p>{@code RoleTest} asserts that these stay in step with the enum constants.
     */
    public static final class Names {

        public static final String ADMIN = "ADMIN";
        public static final String MANAGER = "MANAGER";
        public static final String USER = "USER";

        private Names() {
        }
    }

    /**
     * The Spring Security authority name for this role.
     *
     * <p>Spring's {@code hasRole(...)} expression prepends {@code ROLE_}, so the
     * granted authority has to carry the prefix while the expression must not.
     */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}

package com.fintrack.util;

import java.util.Locale;

/**
 * Canonical form for login emails.
 *
 * <p>Applied at every write path so the database only ever holds one spelling of an
 * address. Without it, {@code Alice@Example.com} and {@code alice@example.com} would
 * both satisfy the unique index and become two separate accounts, and a login would
 * succeed or fail depending on how the address happened to be typed.
 *
 * <p>Only the case and surrounding whitespace are normalized. Provider-specific
 * tricks such as stripping dots or plus-tag suffixes are left alone: they are not
 * universal, and applying them would merge addresses that some mail systems treat
 * as genuinely distinct.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * @param email raw input, may be null
     * @return the trimmed, lower-cased address, or null if the input was null
     */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        // Locale.ROOT, not the default locale: in Turkish the upper-case I lower-cases
        // to a dotless variant, which would silently mangle addresses on a Turkish server.
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

package com.fintrack.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Binds the {@code fintrack.jwt.*} configuration.
 *
 * <p>Typed configuration rather than scattered {@code @Value} lookups: the
 * constraints below are checked while the context is being built, so a deployment
 * missing {@code JWT_SECRET} fails immediately and loudly at startup instead of
 * throwing on the first login attempt in production.
 *
 * @param secret       Base64-encoded signing key, at least 256 bits once decoded
 * @param expirationMs how long an issued token stays valid, in milliseconds
 * @param issuer       the {@code iss} claim, verified on every incoming token
 */
@Validated
@ConfigurationProperties(prefix = "fintrack.jwt")
public record JwtProperties(

        @NotBlank(message = "fintrack.jwt.secret must be set (see JWT_SECRET in .env.example)")
        String secret,

        @Positive(message = "fintrack.jwt.expiration-ms must be positive")
        long expirationMs,

        @NotBlank(message = "fintrack.jwt.issuer must be set")
        String issuer
) {

    public Duration expiration() {
        return Duration.ofMillis(expirationMs);
    }

    /** Seconds until expiry, which is what the token response reports to clients. */
    public long expiresInSeconds() {
        return expiration().toSeconds();
    }
}

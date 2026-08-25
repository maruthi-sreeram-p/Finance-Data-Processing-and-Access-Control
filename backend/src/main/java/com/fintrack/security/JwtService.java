package com.fintrack.security;

import com.fintrack.config.JwtProperties;
import com.fintrack.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies the JSON Web Tokens used to authenticate API calls.
 *
 * <p>Scope is deliberately narrow: this class knows how to mint a token and how to
 * read a trustworthy user id back out of one. It performs no database access and
 * makes no authorization decisions, which keeps the cryptographic surface small
 * enough to reason about and easy to test in isolation.
 */
@Service
@Slf4j
public class JwtService {

    /**
     * HS256 requires a key of at least 256 bits. A shorter one would be rejected by
     * the library at signing time; checking it here means a misconfigured deployment
     * fails at startup rather than at the first login.
     */
    private static final int MINIMUM_KEY_BYTES = 32;

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildSigningKey(properties.secret());
    }

    /**
     * Mints a signed token for the given account.
     *
     * <p>The subject is the numeric user id, not the email. An email is mutable: if
     * a user changed theirs, subject-by-email would either invalidate live tokens or,
     * worse, resolve to a different account should the old address ever be reused.
     * The id is immutable for the lifetime of the account.
     *
     * <p>The email and role claims are carried for the convenience of clients and
     * for debugging. Nothing on the server trusts them -- authorization is derived
     * from the account as it is loaded on each request, so a token issued before a
     * demotion cannot be used to retain the old role.
     */
    public String generateToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.expiration());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(properties.issuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies a token and extracts the user id it identifies.
     *
     * <p>Returns an empty {@link Optional} for every failure mode -- bad signature,
     * expired, wrong issuer, malformed, unparsable subject -- rather than throwing.
     * A rejected token is an ordinary event on a public endpoint, not an exceptional
     * one, and collapsing the causes means the response cannot tell an attacker
     * which part of their forgery was wrong.
     *
     * @return the authenticated user id, or empty if the token is not trustworthy
     */
    public Optional<Long> extractUserId(String token) {
        return parseClaims(token).map(Claims::getSubject).flatMap(JwtService::parseUserId);
    }

    /** @return how long an issued token remains valid, for the login response */
    public long expiresInSeconds() {
        return properties.expiresInSeconds();
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    // Verifying the issuer stops a token minted by a different service
                    // that happens to share the signing key from being accepted here.
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            // Debug, not warn: an expired token is routine traffic, and logging every
            // rejection at warning level turns a scan into a log-flooding attack.
            // The token itself is never logged -- it is a bearer credential.
            log.debug("Rejected JWT: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<Long> parseUserId(String subject) {
        try {
            return Optional.of(Long.valueOf(subject));
        } catch (NumberFormatException e) {
            log.debug("Rejected JWT: subject is not a numeric user id");
            return Optional.empty();
        }
    }

    private static SecretKey buildSigningKey(String base64Secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (DecodingException e) {
            throw new IllegalStateException(
                    "fintrack.jwt.secret must be Base64-encoded; generate one with: openssl rand -base64 32", e);
        }

        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "fintrack.jwt.secret must decode to at least %d bytes for HS256 but was %d; generate one with: openssl rand -base64 32"
                            .formatted(MINIMUM_KEY_BYTES, keyBytes.length));
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}

package com.fintrack.security;

import com.fintrack.config.JwtProperties;
import com.fintrack.entity.User;
import com.fintrack.util.TestDataFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token issuing and verification.
 *
 * <p>The rejection cases matter more than the happy path: a token that should be
 * refused but is not is a full authentication bypass, and it is the kind of defect
 * that no functional test would notice, because everything keeps working.
 *
 * <p>The expired and foreign-key tokens are forged here with jjwt directly rather
 * than by waiting or by reconfiguring the service, so the tests are deterministic
 * and carry no sleeps.
 */
class JwtServiceTest {

    /** 32 bytes once decoded, the minimum HS256 accepts. */
    private static final String SECRET =
            Base64.getEncoder().encodeToString("fintrack-unit-test-signing-key!!".getBytes(StandardCharsets.UTF_8));

    private static final String ISSUER = "fintrack-test";

    private final JwtProperties properties = new JwtProperties(SECRET, 3_600_000L, ISSUER);
    private final JwtService jwtService = new JwtService(properties);

    private final User user = userWithId(42L);

    @Test
    @DisplayName("issues a token that verifies back to the same user id")
    void issuesVerifiableToken() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUserId(token)).contains(42L);
    }

    @Test
    @DisplayName("puts the immutable user id in the subject, not the mutable email")
    void subjectIsTheUserId() {
        String token = jwtService.generateToken(user);

        String subject = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();

        // Subject-by-email would break every live token when an address changed, or
        // resolve to a different account if one were ever reused.
        assertThat(subject).isEqualTo("42");
    }

    @Test
    @DisplayName("rejects a token whose signature has been tampered with")
    void rejectsTamperedSignature() {
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "forged";

        assertThat(jwtService.extractUserId(tampered)).isEmpty();
    }

    @Test
    @DisplayName("rejects a token whose payload has been edited")
    void rejectsEditedPayload() {
        String token = jwtService.generateToken(user);
        String[] parts = token.split("\\.");

        // Swap the subject for another user id and keep the original signature: the
        // classic privilege-escalation attempt against a JWT.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"iss\":\"%s\"}".formatted(ISSUER).getBytes(StandardCharsets.UTF_8));

        assertThat(jwtService.extractUserId(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    @DisplayName("rejects a token signed with a different key")
    void rejectsForeignSigningKey() {
        SecretKey foreignKey = Keys.hmacShaKeyFor(
                "a-completely-different-signing-key!!!".getBytes(StandardCharsets.UTF_8));

        String foreignToken = Jwts.builder()
                .subject("42")
                .issuer(ISSUER)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(foreignKey, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.extractUserId(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject("42")
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.extractUserId(expired)).isEmpty();
    }

    @Test
    @DisplayName("rejects a correctly signed token issued by someone else")
    void rejectsWrongIssuer() {
        // Guards against a sibling service that shares the signing key having its
        // tokens silently accepted here.
        String otherIssuer = Jwts.builder()
                .subject("42")
                .issuer("some-other-service")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.extractUserId(otherIssuer)).isEmpty();
    }

    @Test
    @DisplayName("rejects a token whose subject is not a user id")
    void rejectsNonNumericSubject() {
        String badSubject = Jwts.builder()
                .subject("not-a-number")
                .issuer(ISSUER)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.extractUserId(badSubject)).isEmpty();
    }

    @Test
    @DisplayName("returns empty rather than throwing for structurally invalid input")
    void rejectsGarbageWithoutThrowing() {
        // A rejected token is ordinary traffic on a public endpoint, not an
        // exceptional condition, and every failure mode looks the same from outside.
        for (String garbage : new String[]{"", "   ", "not.a.jwt", "onlyonepart", "a.b", "a.b.c.d"}) {
            assertThat(jwtService.extractUserId(garbage))
                    .as("token: '%s'", garbage)
                    .isEqualTo(Optional.empty());
        }
    }

    @Test
    @DisplayName("refuses to start with a secret shorter than HS256 requires")
    void refusesWeakSecret() {
        // Fails while the context is being built rather than at the first login.
        String tooShort = Base64.getEncoder().encodeToString("only-16-bytes!!!".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtService(new JwtProperties(tooShort, 3_600_000L, ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("refuses to start with a secret that is not Base64")
    void refusesNonBase64Secret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("not valid base64 !!!", 3_600_000L, ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("reports the configured lifetime in seconds")
    void reportsLifetimeInSeconds() {
        assertThat(jwtService.expiresInSeconds()).isEqualTo(3600L);
    }

    // --- Helpers -------------------------------------------------------------

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private static User userWithId(Long id) {
        User user = TestDataFactory.user("jwt@fintrack.test");
        user.setId(id);
        return user;
    }
}

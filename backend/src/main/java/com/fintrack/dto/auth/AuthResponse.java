package com.fintrack.dto.auth;

import com.fintrack.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of a successful registration or login.
 *
 * <p>The profile is returned alongside the token so a client does not have to
 * follow every login with a call to {@code /api/users/me} just to learn the user's
 * name and role.
 *
 * @param accessToken signed JWT to send as {@code Authorization: Bearer <token>}
 * @param tokenType   the scheme to use, always {@code Bearer}
 * @param expiresIn   token lifetime in seconds
 * @param user        the authenticated account
 */
@Schema(description = "An issued access token together with the account it belongs to")
public record AuthResponse(

        @Schema(description = "Signed JWT for the Authorization header",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
        String accessToken,

        @Schema(description = "Authorization scheme", example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds", example = "3600")
        long expiresIn,

        @Schema(description = "The authenticated account")
        UserResponse user
) {

    private static final String BEARER = "Bearer";

    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, BEARER, expiresInSeconds, user);
    }
}

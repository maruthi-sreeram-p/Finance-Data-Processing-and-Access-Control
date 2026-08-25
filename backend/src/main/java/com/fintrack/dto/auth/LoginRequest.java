package com.fintrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/auth/login}.
 *
 * <p>Only {@code @NotBlank} here, with none of the size or complexity rules that
 * guard registration. Applying those at login would reject a stored password
 * whenever the policy is tightened, and the shape of the rejection would tell an
 * attacker which of their guesses were even eligible to be checked.
 */
@Schema(description = "Credentials exchanged for a JWT")
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Schema(description = "Registered login email", example = "abhiram@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Password is required")
        @Schema(description = "Account password", example = "Passw0rd123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}

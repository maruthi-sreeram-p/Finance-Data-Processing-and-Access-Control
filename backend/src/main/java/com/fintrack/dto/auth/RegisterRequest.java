package com.fintrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}.
 *
 * <p>There is deliberately no role field. Accepting one on a public endpoint --
 * even "just to make seeding easier" -- would let anyone on the internet create an
 * administrator by adding a line to the request body. The first account is granted
 * USER unconditionally, and only an existing administrator can promote it.
 */
@Schema(description = "Self-service registration. New accounts always receive the USER role.")
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(description = "Display name", example = "Abhiram P", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(description = "Login email; must not already be registered",
                example = "abhiram@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Password is required")
        // The 72-character ceiling is BCrypt's, not an arbitrary choice: it ignores
        // everything beyond 72 bytes, so longer passwords would be interchangeable
        // at login with their own truncations.
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$",
                message = "Password must contain at least one letter and one digit"
        )
        @Schema(description = "At least 8 characters, including a letter and a digit",
                example = "Passw0rd123", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}

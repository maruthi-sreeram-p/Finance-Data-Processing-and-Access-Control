package com.fintrack.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile update for {@code PUT /api/users/me}.
 *
 * <p>Both fields are optional: a null field means "leave unchanged", which lets a
 * client send only what it is actually editing. Note what is absent -- role and
 * status. Accepting them here would let any user promote themselves to ADMIN, so
 * they are only settable through the admin endpoint.
 */
@Schema(description = "Fields a user may change on their own profile. Omit a field to leave it unchanged.")
public record UpdateUserRequest(
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(description = "New display name", example = "Abhiram P")
        String name,

        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(description = "New login email; must not already be registered", example = "abhiram@example.com")
        String email
) {
}

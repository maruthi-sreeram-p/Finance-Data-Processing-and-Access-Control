package com.fintrack.dto.user;

import com.fintrack.entity.Role;
import com.fintrack.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Public projection of a user account.
 *
 * <p>There is no password field and no setter that could add one: because this is
 * a record with an explicit component list, a hash cannot leak into a response by
 * someone adding a field to the entity.
 */
@Schema(description = "A user account as exposed by the API")
public record UserResponse(
        @Schema(description = "Unique identifier", example = "1")
        Long id,

        @Schema(description = "Display name", example = "Abhiram P")
        String name,

        @Schema(description = "Login email", example = "abhiram@example.com")
        String email,

        @Schema(description = "Access role", example = "USER")
        Role role,

        @Schema(description = "Account lifecycle status", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "When the account was created")
        Instant createdAt,

        @Schema(description = "When the account was last modified")
        Instant updatedAt
) {
}

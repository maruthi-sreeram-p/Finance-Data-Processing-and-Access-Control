package com.fintrack.dto.user;

import com.fintrack.entity.Role;
import com.fintrack.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Administrative update for {@code PUT /api/users/{id}}.
 *
 * <p>This is a separate type from {@link UpdateUserRequest} rather than the same
 * record with extra fields, because the difference between them <em>is</em> the
 * privilege boundary: role and status are reachable only through the ADMIN-guarded
 * endpoint that binds this type. Sharing one DTO across both endpoints is how mass-
 * assignment privilege escalation gets introduced.
 *
 * <p>Every field is optional; a null means "leave unchanged".
 */
@Schema(description = "Fields an administrator may change on any account. Omit a field to leave it unchanged.")
public record AdminUpdateUserRequest(
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(description = "New display name", example = "Abhiram P")
        String name,

        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(description = "New login email; must not already be registered", example = "abhiram@example.com")
        String email,

        @Schema(description = "New access role", example = "MANAGER")
        Role role,

        @Schema(description = "New account status", example = "SUSPENDED")
        UserStatus status
) {
}

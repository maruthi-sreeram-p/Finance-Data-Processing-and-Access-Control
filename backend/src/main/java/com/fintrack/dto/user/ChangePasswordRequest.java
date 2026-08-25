package com.fintrack.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/users/me/password}.
 *
 * <p>The current password is required even though the caller is already
 * authenticated: a stolen or leaked JWT would otherwise be enough to take
 * permanent ownership of the account by changing its password.
 */
@Schema(description = "Password change request; the current password is re-verified")
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Schema(description = "The password currently in use", example = "OldPassw0rd")
        String currentPassword,

        @NotBlank(message = "New password is required")
        // The upper bound is not arbitrary: BCrypt silently ignores everything past
        // 72 bytes, so allowing longer input would make two different passwords
        // interchangeable at login.
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$",
                message = "Password must contain at least one letter and one digit"
        )
        @Schema(description = "The replacement password", example = "NewPassw0rd")
        String newPassword
) {
}

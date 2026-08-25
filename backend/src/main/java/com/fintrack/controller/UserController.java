package com.fintrack.controller;

import com.fintrack.dto.common.PagedResponse;
import com.fintrack.dto.user.AdminUpdateUserRequest;
import com.fintrack.dto.user.ChangePasswordRequest;
import com.fintrack.dto.user.UpdateUserRequest;
import com.fintrack.dto.user.UserResponse;
import com.fintrack.entity.Role;
import com.fintrack.entity.UserStatus;
import com.fintrack.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account endpoints.
 *
 * <p>Two distinct groups live here. The {@code /me} routes act on whoever is
 * calling and need no id, so there is no id for a caller to tamper with. The
 * id-addressed routes are administrative and are gated by {@code @PreAuthorize}.
 *
 * <p>The controller holds no logic beyond binding and status-code selection --
 * every rule, including the privilege checks that cannot be expressed as an
 * annotation, lives in {@link UserService}.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Profile self-service and account administration")
public class UserController {

    private final UserService userService;

    // --- Self-service --------------------------------------------------------

    @GetMapping("/me")
    @Operation(
            summary = "Get the authenticated user",
            description = "Returns the profile of the account behind the presented token."
    )
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    @PutMapping("/me")
    @Operation(
            summary = "Update the authenticated user",
            description = "Updates the caller's own name and/or email. "
                    + "Role and status are not accepted here and can only be changed by an administrator."
    )
    public ResponseEntity<UserResponse> updateCurrentUser(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUser(request));
    }

    @PutMapping("/me/password")
    @Operation(
            summary = "Change the authenticated user's password",
            description = "Requires the current password, so a stolen token alone cannot take over the account."
    )
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeCurrentUserPassword(request);
        // 204: the change succeeded and there is nothing meaningful to return.
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    @Operation(
            summary = "Deactivate the authenticated user",
            description = "Soft delete: the account is marked INACTIVE and can no longer authenticate. "
                    + "The financial history is retained, so the row is never physically removed."
    )
    public ResponseEntity<Void> deactivateCurrentUser() {
        userService.deactivateCurrentUser();
        return ResponseEntity.noContent().build();
    }

    // --- Administrative ------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
    @Operation(
            summary = "List accounts",
            description = "Administrator only. Optionally filtered by role and/or status, always paginated."
    )
    public ResponseEntity<PagedResponse<UserResponse>> listUsers(
            @Parameter(description = "Only return accounts with this role")
            @RequestParam(required = false) Role role,

            @Parameter(description = "Only return accounts in this status")
            @RequestParam(required = false) UserStatus status,

            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(userService.listUsers(role, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
    @Operation(summary = "Get an account by id", description = "Administrator only.")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
    @Operation(
            summary = "Update an account",
            description = "Administrator only. This is the only route through which role and status can change."
    )
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request) {

        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
    @Operation(
            summary = "Deactivate an account",
            description = "Administrator only. Soft delete, as for self-deactivation. "
                    + "Refuses to act on the caller's own account and on the last active administrator."
    )
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}

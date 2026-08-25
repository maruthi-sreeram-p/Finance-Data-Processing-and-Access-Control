package com.fintrack.service;

import com.fintrack.dto.common.PagedResponse;
import com.fintrack.dto.user.AdminUpdateUserRequest;
import com.fintrack.dto.user.ChangePasswordRequest;
import com.fintrack.dto.user.UpdateUserRequest;
import com.fintrack.dto.user.UserResponse;
import com.fintrack.entity.Role;
import com.fintrack.entity.UserStatus;
import org.springframework.data.domain.Pageable;

/**
 * Account management.
 *
 * <p>The self-service and administrative operations are separate methods rather
 * than one method with an "isAdmin" flag, so that the privilege boundary is visible
 * in the signature and each endpoint can only reach the operations it is allowed to.
 */
public interface UserService {

    /** @return the profile of the currently authenticated caller */
    UserResponse getCurrentUser();

    /** Updates the caller's own name and/or email. Cannot change role or status. */
    UserResponse updateCurrentUser(UpdateUserRequest request);

    /** Re-verifies the current password, then replaces it. */
    void changeCurrentUserPassword(ChangePasswordRequest request);

    /**
     * Deactivates the caller's own account.
     *
     * <p>A soft delete: the row is marked {@link UserStatus#INACTIVE} rather than
     * removed, because deleting it would cascade away the user's entire financial
     * history and break the foreign keys that reference it.
     */
    void deactivateCurrentUser();

    // --- Administrative operations -----------------------------------------

    PagedResponse<UserResponse> listUsers(Role role, UserStatus status, Pageable pageable);

    UserResponse getUserById(Long id);

    UserResponse updateUser(Long id, AdminUpdateUserRequest request);

    /** Deactivates any account. Refuses to act on the caller's own account. */
    void deactivateUser(Long id);
}

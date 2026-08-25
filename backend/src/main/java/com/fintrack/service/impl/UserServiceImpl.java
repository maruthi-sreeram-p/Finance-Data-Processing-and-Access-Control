package com.fintrack.service.impl;

import com.fintrack.dto.common.PagedResponse;
import com.fintrack.dto.user.AdminUpdateUserRequest;
import com.fintrack.dto.user.ChangePasswordRequest;
import com.fintrack.dto.user.UpdateUserRequest;
import com.fintrack.dto.user.UserResponse;
import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.entity.UserStatus;
import com.fintrack.exception.DuplicateResourceException;
import com.fintrack.exception.ForbiddenException;
import com.fintrack.exception.ResourceNotFoundException;
import com.fintrack.exception.UnauthorizedException;
import com.fintrack.mapper.UserMapper;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.CurrentUserProvider;
import com.fintrack.service.UserService;
import com.fintrack.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String RESOURCE = "User";

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // --- Self-service --------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return userMapper.toResponse(currentUserProvider.requireCurrentUser());
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        User user = currentUserProvider.requireCurrentUser();

        applyName(user, request.name());
        applyEmail(user, request.email());

        User saved = userRepository.save(user);
        log.info("User {} updated their own profile", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void changeCurrentUserPassword(ChangePasswordRequest request) {
        User user = currentUserProvider.requireCurrentUser();

        // Re-verifying the current password means a stolen token alone is not enough
        // to lock the real owner out of their account.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("Rejected password change for user {}: current password did not match", user.getId());
            throw new UnauthorizedException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ForbiddenException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Only the fact of the change is logged, never the passwords involved.
        log.info("User {} changed their password", user.getId());
    }

    @Override
    @Transactional
    public void deactivateCurrentUser() {
        User user = currentUserProvider.requireCurrentUser();

        // The last administrator may not lock themselves out; there would be no way
        // back into the system afterwards.
        guardLastAdministrator(user);

        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("User {} deactivated their own account", user.getId());
    }

    // --- Administrative ------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(Role role, UserStatus status, Pageable pageable) {
        Page<User> users = findFiltered(role, status, pageable);
        return PagedResponse.from(users, userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userMapper.toResponse(loadUser(id));
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, AdminUpdateUserRequest request) {
        User user = loadUser(id);

        applyName(user, request.name());
        applyEmail(user, request.email());
        applyRole(user, request.role());
        applyStatus(user, request.status());

        User saved = userRepository.save(user);
        log.info("Administrator updated account {}", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivateUser(Long id) {
        User user = loadUser(id);

        // Routing self-deactivation through the dedicated /me endpoint keeps the audit
        // trail honest about who did what, and stops an administrator from accidentally
        // disabling themselves while managing others.
        if (user.getId().equals(currentUserProvider.requireCurrentUserId())) {
            throw new ForbiddenException(
                    "Administrators cannot deactivate their own account here; use DELETE /api/users/me");
        }

        guardLastAdministrator(user);

        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("Administrator deactivated account {}", user.getId());
    }

    // --- Helpers -------------------------------------------------------------

    private Page<User> findFiltered(Role role, UserStatus status, Pageable pageable) {
        if (role != null && status != null) {
            return userRepository.findByRoleAndStatus(role, status, pageable);
        }
        if (role != null) {
            return userRepository.findByRole(role, pageable);
        }
        if (status != null) {
            return userRepository.findByStatus(status, pageable);
        }
        return userRepository.findAll(pageable);
    }

    private User loadUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private void applyName(User user, String name) {
        if (name != null) {
            user.setName(name.trim());
        }
    }

    /**
     * Applies an email change, normalizing it and rejecting one that is already taken.
     *
     * <p>The uniqueness check is a courtesy that produces a clean 409 instead of a
     * constraint-violation stack trace; the unique index is what actually guarantees
     * it under concurrency.
     */
    private void applyEmail(User user, String email) {
        if (email == null) {
            return;
        }

        String normalized = EmailNormalizer.normalize(email);
        if (normalized.equals(user.getEmail())) {
            return;
        }

        if (userRepository.existsByEmail(normalized)) {
            throw DuplicateResourceException.of(RESOURCE, "email", normalized);
        }
        user.setEmail(normalized);
    }

    private void applyRole(User user, Role role) {
        if (role == null || role == user.getRole()) {
            return;
        }
        if (user.isAdmin()) {
            guardLastAdministrator(user);
        }
        user.setRole(role);
    }

    private void applyStatus(User user, UserStatus status) {
        if (status == null || status == user.getStatus()) {
            return;
        }
        if (status != UserStatus.ACTIVE) {
            guardLastAdministrator(user);
        }
        user.setStatus(status);
    }

    /**
     * Refuses any change that would leave the system with no usable administrator.
     *
     * <p>Without this, demoting or suspending the final ADMIN would make user
     * administration permanently unreachable, recoverable only by editing the
     * database by hand.
     */
    private void guardLastAdministrator(User user) {
        if (user.getRole() != Role.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        if (userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ForbiddenException(
                    "This is the only active administrator account and cannot be removed or demoted");
        }
    }
}

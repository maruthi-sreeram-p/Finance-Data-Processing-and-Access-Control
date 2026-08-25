package com.fintrack.service;

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
import com.fintrack.service.impl.UserServiceImpl;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the account rules.
 *
 * <p>Pure Mockito with no Spring context: these assert business behaviour, and
 * booting a container to do it would make the suite an order of magnitude slower
 * without testing anything extra. Wiring is covered by the integration tests.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    // The mapper is trivial and has no collaborators, so the real one is used --
    // mocking it would only assert that the test knows what the mapper does.
    private final UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserServiceImpl userService;

    private User caller;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, currentUserProvider, passwordEncoder, userMapper);
        caller = TestDataFactory.user("caller@fintrack.test");
        caller.setId(1L);
    }

    @Nested
    @DisplayName("profile")
    class Profile {

        @Test
        @DisplayName("returns the caller without exposing the password hash")
        void returnsCallerProfile() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);

            UserResponse response = userService.getCurrentUser();

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("caller@fintrack.test");
            // UserResponse has no password component at all; this documents that the
            // projection is the reason a hash cannot leak, not a runtime filter.
            assertThat(UserResponse.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("password", "passwordHash");
        }

        @Test
        @DisplayName("normalizes a new email to lower case before storing it")
        void normalizesEmailOnUpdate() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);
            when(userRepository.existsByEmail("New.Address@Example.COM".toLowerCase())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.updateCurrentUser(new UpdateUserRequest(null, "  New.Address@Example.COM  "));

            assertThat(caller.getEmail()).isEqualTo("new.address@example.com");
        }

        @Test
        @DisplayName("rejects an email that another account already uses")
        void rejectsDuplicateEmail() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);
            when(userRepository.existsByEmail("taken@fintrack.test")).thenReturn(true);

            assertThatThrownBy(() ->
                    userService.updateCurrentUser(new UpdateUserRequest(null, "taken@fintrack.test")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("taken@fintrack.test");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("treats a null field as leave-unchanged rather than clear")
        void leavesOmittedFieldsUntouched() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);
            when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.updateCurrentUser(new UpdateUserRequest("Renamed", null));

            assertThat(caller.getName()).isEqualTo("Renamed");
            assertThat(caller.getEmail()).isEqualTo("caller@fintrack.test");
        }

        @Test
        @DisplayName("does not treat re-submitting the unchanged email as a duplicate")
        void allowsResubmittingOwnEmail() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);
            when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.updateCurrentUser(new UpdateUserRequest("Renamed", "CALLER@fintrack.test"));

            assertThat(caller.getEmail()).isEqualTo("caller@fintrack.test");
            verify(userRepository, never()).existsByEmail(anyString());
        }
    }

    @Nested
    @DisplayName("password change")
    class PasswordChange {

        @Test
        @DisplayName("refuses when the supplied current password does not match")
        void refusesWrongCurrentPassword() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);
            when(passwordEncoder.matches("wrong", caller.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> userService.changeCurrentUserPassword(
                    new ChangePasswordRequest("wrong", "BrandNew1")))
                    .isInstanceOf(UnauthorizedException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses a new password identical to the current one")
        void refusesUnchangedPassword() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);
            when(passwordEncoder.matches("Current1", caller.getPasswordHash())).thenReturn(true);

            assertThatThrownBy(() -> userService.changeCurrentUserPassword(
                    new ChangePasswordRequest("Current1", "Current1")))
                    .isInstanceOf(ForbiddenException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("stores the hash of the new password, never the password itself")
        void storesHashOfNewPassword() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);
            when(passwordEncoder.matches("Current1", caller.getPasswordHash())).thenReturn(true);
            when(passwordEncoder.matches("BrandNew1", caller.getPasswordHash())).thenReturn(false);
            when(passwordEncoder.encode("BrandNew1")).thenReturn("$2a$12$hashed");

            userService.changeCurrentUserPassword(new ChangePasswordRequest("Current1", "BrandNew1"));

            assertThat(caller.getPasswordHash()).isEqualTo("$2a$12$hashed");
            assertThat(caller.getPasswordHash()).isNotEqualTo("BrandNew1");
            verify(userRepository).save(caller);
        }
    }

    @Nested
    @DisplayName("deactivation")
    class Deactivation {

        @Test
        @DisplayName("soft-deletes by marking the account inactive")
        void softDeletesOwnAccount() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(caller);

            userService.deactivateCurrentUser();

            assertThat(caller.getStatus()).isEqualTo(UserStatus.INACTIVE);
            // The row survives: deleting it would cascade away the whole ledger.
            verify(userRepository).save(caller);
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("refuses to deactivate the only remaining administrator")
        void protectsTheLastAdministrator() {
            User onlyAdmin = TestDataFactory.admin();
            onlyAdmin.setId(1L);
            when(currentUserProvider.requireCurrentUser()).thenReturn(onlyAdmin);
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> userService.deactivateCurrentUser())
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("only active administrator");

            assertThat(onlyAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("allows deactivating an administrator when others remain")
        void allowsDeactivationWhenOtherAdminsExist() {
            User admin = TestDataFactory.admin();
            admin.setId(1L);
            when(currentUserProvider.requireCurrentUser()).thenReturn(admin);
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(3L);

            userService.deactivateCurrentUser();

            assertThat(admin.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("refuses when an administrator targets their own id")
        void refusesAdminSelfTargeting() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(caller));
            when(currentUserProvider.requireCurrentUserId()).thenReturn(1L);

            assertThatThrownBy(() -> userService.deactivateUser(1L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("/api/users/me");
        }
    }

    @Nested
    @DisplayName("administration")
    class Administration {

        @Test
        @DisplayName("reports a missing account as not found")
        void reportsMissingAccount() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("changes role and status, which the self-service endpoint cannot")
        void changesRoleAndStatus() {
            User target = TestDataFactory.user("target@fintrack.test");
            target.setId(7L);
            when(userRepository.findById(7L)).thenReturn(Optional.of(target));
            when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            UserResponse response = userService.updateUser(7L,
                    new AdminUpdateUserRequest(null, null, Role.MANAGER, UserStatus.SUSPENDED));

            assertThat(response.role()).isEqualTo(Role.MANAGER);
            assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("refuses to demote the only remaining administrator")
        void refusesDemotingTheLastAdministrator() {
            User onlyAdmin = TestDataFactory.admin();
            onlyAdmin.setId(2L);
            when(userRepository.findById(2L)).thenReturn(Optional.of(onlyAdmin));
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> userService.updateUser(2L,
                    new AdminUpdateUserRequest(null, null, Role.USER, null)))
                    .isInstanceOf(ForbiddenException.class);

            assertThat(onlyAdmin.getRole()).isEqualTo(Role.ADMIN);
        }
    }
}

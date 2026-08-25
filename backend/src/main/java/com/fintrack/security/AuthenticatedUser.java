package com.fintrack.security;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The authenticated principal: an immutable snapshot of the fields Spring Security
 * needs, detached from the JPA entity.
 *
 * <p>Putting the {@link User} entity itself in the security context would keep a
 * detached, lazily-mapped object -- password hash included -- alive for the whole
 * request and expose it to anything that reads the principal. This carries the id
 * and the authorities and nothing else that matters.
 *
 * <p>The account-state flags are wired to {@link UserStatus} so a suspended user is
 * rejected by the framework itself rather than by a check someone has to remember
 * to write.
 */
public class AuthenticatedUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final UserStatus status;

    public AuthenticatedUser(Long id, String email, String passwordHash, Role role, UserStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole(),
                user.getStatus()
        );
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security's "username" is FinTrack's login email. */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Suspended and inactive accounts are reported as disabled, which makes Spring
     * Security refuse the authentication before any endpoint logic runs.
     */
    @Override
    public boolean isEnabled() {
        return status.canAuthenticate();
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDED;
    }

    @Override
    public String toString() {
        // No password hash: principals end up in debug logs and error reports.
        return "AuthenticatedUser{id=%d, email='%s', role=%s, status=%s}"
                .formatted(id, email, role, status);
    }
}

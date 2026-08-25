package com.fintrack.security;

import com.fintrack.entity.User;
import com.fintrack.exception.UnauthorizedException;
import com.fintrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the caller from Spring Security's {@code SecurityContext} and loads the
 * matching account.
 *
 * <p>The account is re-read from the database on every request rather than trusted
 * from the token. A JWT is a snapshot of who the user was when it was issued: if an
 * administrator suspends the account or demotes the role, a token minted a minute
 * earlier would otherwise keep working until it expired. Reloading costs one indexed
 * lookup and makes revocation take effect immediately.
 */
@Component
@RequiredArgsConstructor
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required to access this resource");
        }

        // An anonymous request carries a fully populated Authentication whose
        // principal is the literal string "anonymousUser", so isAuthenticated()
        // alone is not enough to tell a real caller from an unauthenticated one.
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return reload(authenticatedUser.getId());
        }

        throw new UnauthorizedException("Authentication is required to access this resource");
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Long requireCurrentUserId() {
        // Avoids a database round trip when only the id is needed, which is the
        // common case for ownership-scoped queries.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.getId();
        }

        throw new UnauthorizedException("Authentication is required to access this resource");
    }

    private User reload(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        "The authenticated account no longer exists"));
    }
}

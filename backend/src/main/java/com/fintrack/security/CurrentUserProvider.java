package com.fintrack.security;

import com.fintrack.entity.User;

/**
 * Resolves the account behind the current request.
 *
 * <p>This exists as an interface so that the rest of the application never touches
 * {@code SecurityContextHolder} directly. Services ask "who is calling?" and stay
 * ignorant of whether the answer came from a JWT, HTTP Basic, or a test fixture --
 * which is also what makes them straightforward to unit test.
 */
public interface CurrentUserProvider {

    /**
     * @return the authenticated user
     * @throws com.fintrack.exception.UnauthorizedException if the request is anonymous
     *         or the account behind it no longer exists
     */
    User requireCurrentUser();

    /**
     * @return the authenticated user's id
     * @throws com.fintrack.exception.UnauthorizedException if the request is anonymous
     */
    default Long requireCurrentUserId() {
        return requireCurrentUser().getId();
    }
}

package com.fintrack.service;

import com.fintrack.dto.auth.AuthResponse;
import com.fintrack.dto.auth.LoginRequest;
import com.fintrack.dto.auth.RegisterRequest;

/**
 * Registration and login.
 *
 * <p>Kept separate from {@code UserService} because the two answer different
 * questions. This one establishes identity -- creating credentials and proving
 * them -- while {@code UserService} manages accounts that are already identified.
 */
public interface AuthenticationService {

    /**
     * Creates an account and immediately issues a token for it.
     *
     * @throws com.fintrack.exception.DuplicateResourceException if the email is taken
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Verifies credentials and issues a token.
     *
     * @throws com.fintrack.exception.UnauthorizedException if the credentials are wrong
     *         or the account is not active
     */
    AuthResponse login(LoginRequest request);
}

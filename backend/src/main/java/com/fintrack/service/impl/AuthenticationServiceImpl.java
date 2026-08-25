package com.fintrack.service.impl;

import com.fintrack.dto.auth.AuthResponse;
import com.fintrack.dto.auth.LoginRequest;
import com.fintrack.dto.auth.RegisterRequest;
import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.entity.UserStatus;
import com.fintrack.exception.DuplicateResourceException;
import com.fintrack.exception.UnauthorizedException;
import com.fintrack.mapper.UserMapper;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.AuthenticatedUser;
import com.fintrack.security.JwtService;
import com.fintrack.service.AuthenticationService;
import com.fintrack.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    /**
     * Public registration always produces this role.
     *
     * <p>A constant, not a parameter: the moment the caller can influence it, the
     * endpoint becomes a self-service route to administrator.
     */
    private static final Role DEFAULT_ROLE = Role.USER;

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());

        // Checked up front so the common case returns a clean 409 rather than a
        // constraint-violation stack trace. The unique index below is what actually
        // makes this safe: two simultaneous registrations can both pass this check.
        if (userRepository.existsByEmail(email)) {
            throw DuplicateResourceException.of("User", "email", email);
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                // Hashed before it is ever assigned to the entity, so no code path
                // exists on which a plaintext password could reach the database.
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(DEFAULT_ROLE)
                .status(UserStatus.ACTIVE)
                .build();

        User saved = persist(user, email);
        log.info("Registered account {} with role {}", saved.getId(), saved.getRole());

        return issueToken(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());

        Authentication authentication;
        try {
            // Delegated to Spring Security rather than comparing hashes here: the
            // provider handles the password check, the account-status flags and the
            // constant-time comparison, and it hashes even for an unknown email so
            // the response time does not reveal whether the address exists.
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            // Every failure -- unknown email, wrong password, suspended, inactive --
            // is reported identically. Naming the cause would let an attacker
            // enumerate registered addresses and identify valuable locked accounts.
            // The reason is recorded server-side only.
            log.warn("Failed login attempt for {}: {}", email, e.getClass().getSimpleName());
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));

        log.info("Issued token for account {}", user.getId());
        return issueToken(user);
    }

    private User persist(User user, String email) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent registration of the same address.
            // Translated to the same 409 the pre-check produces, so the API behaves
            // consistently whichever path detected the clash.
            throw DuplicateResourceException.of("User", "email", email);
        }
    }

    private AuthResponse issueToken(User user) {
        return AuthResponse.bearer(
                jwtService.generateToken(user),
                jwtService.expiresInSeconds(),
                userMapper.toResponse(user));
    }
}

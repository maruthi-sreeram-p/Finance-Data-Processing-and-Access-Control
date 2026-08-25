package com.fintrack.security;

import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges FinTrack accounts to Spring Security.
 *
 * <p>Two lookups are exposed because the two authentication paths key on different
 * things: password login arrives with an email, while a JWT arrives with the user
 * id from its subject claim.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * Deliberately identical for "no such account" and "wrong password".
     *
     * <p>Distinguishing the two would turn the login endpoint into an oracle for
     * which email addresses are registered.
     */
    private static final String AUTHENTICATION_FAILED = "Invalid email or password";

    private final UserRepository userRepository;

    /**
     * Loads an account by login email, for the username/password authentication path.
     *
     * @param email the login email; normalized here so that the casing a client
     *              happens to send cannot change the outcome
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(EmailNormalizer.normalize(email))
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATION_FAILED));
    }

    /**
     * Loads an account by id, for the JWT path.
     *
     * <p>The account is re-read on every request rather than reconstructed from the
     * token claims. That one indexed lookup is what makes a suspension or a role
     * change take effect immediately instead of whenever the token happens to expire.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException(AUTHENTICATION_FAILED));
        return AuthenticatedUser.from(user);
    }
}

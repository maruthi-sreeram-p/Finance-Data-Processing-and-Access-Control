package com.fintrack.config;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.repository.UserRepository;
import com.fintrack.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Promotes one account to ADMIN on startup. Local development only.
 *
 * <p>FinTrack has a deliberate bootstrap problem: registration always produces a
 * USER, and only an administrator can promote anyone, so a fresh database has no
 * way to grow its first administrator through the API. In a real deployment that
 * is handled once, by hand, with a SQL statement -- which is the correct amount of
 * friction for creating the account that can administer every other account.
 *
 * <p>The local profile keeps its data in memory and is wiped on every restart, so
 * doing that by hand each time would make the administrator screens effectively
 * untestable during frontend work. This closes that gap and nothing else.
 *
 * <p>Three things keep it from becoming a liability:
 * <ul>
 *   <li>{@code @Profile("local")} -- the bean does not exist in any other profile,
 *       so it cannot run in dev or production even by misconfiguration.</li>
 *   <li>It promotes an <em>existing</em> account. It never creates one, and never
 *       sets a password, so it cannot introduce a login nobody knows about.</li>
 *   <li>It does nothing at all unless {@code fintrack.local.admin-email} is set.</li>
 * </ul>
 */
@Slf4j
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalAdminBootstrap {

    @Bean
    public ApplicationRunner promoteLocalAdmin(
            UserRepository userRepository,
            @Value("${fintrack.local.admin-email:}") String adminEmail) {

        return args -> {
            if (adminEmail == null || adminEmail.isBlank()) {
                return;
            }

            String email = EmailNormalizer.normalize(adminEmail);

            userRepository.findByEmail(email).ifPresentOrElse(
                    user -> promote(userRepository, user),
                    // Not an error: the account is usually registered through the UI
                    // after the server is already running, so absence at startup is
                    // the normal case on a fresh in-memory database.
                    () -> log.info(
                            "Local admin bootstrap: no account for {} yet. Register it, then restart to promote.",
                            email));
        };
    }

    private void promote(UserRepository userRepository, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        user.setRole(Role.ADMIN);
        userRepository.save(user);
        log.warn("Local admin bootstrap: promoted {} to ADMIN. This runs only under the local profile.",
                user.getEmail());
    }
}

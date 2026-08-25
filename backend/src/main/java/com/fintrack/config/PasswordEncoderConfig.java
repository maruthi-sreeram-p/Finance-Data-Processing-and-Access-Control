package com.fintrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing.
 *
 * <p>Deliberately kept out of {@code SecurityConfig}. The filter chain depends on
 * an authentication provider, which depends on the encoder, while services need the
 * encoder too -- declaring it alongside the filter chain is the usual way a circular
 * bean dependency gets introduced into a Spring Security setup. A standalone
 * configuration has no such edges.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Cost factor 12 rather than the Spring default of 10.
     *
     * <p>BCrypt's cost is a deliberate slowdown: each increment doubles the work an
     * offline attacker must repeat for every guess. Twelve keeps a single hash in
     * the low hundreds of milliseconds on current hardware, which is imperceptible
     * on a login request and expensive at scale for anyone brute-forcing a stolen
     * dump.
     */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}

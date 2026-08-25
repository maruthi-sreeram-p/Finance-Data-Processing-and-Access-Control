package com.fintrack.config;

import com.fintrack.entity.Role;
import com.fintrack.security.CustomUserDetailsService;
import com.fintrack.security.JwtAuthenticationFilter;
import com.fintrack.security.RestAccessDeniedHandler;
import com.fintrack.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The HTTP security policy.
 *
 * <p>Authorization is layered rather than expressed in one place, and that is
 * intentional:
 * <ol>
 *   <li><b>Here</b>: coarse URL rules -- what is public, what needs a role at all.
 *       Cheap, and enforced before a request reaches any application code.</li>
 *   <li><b>{@code @PreAuthorize}</b> on controllers: role checks stated next to the
 *       method they guard, where they are visible to whoever edits it.</li>
 *   <li><b>The service layer</b>: ownership. No URL pattern can express "this
 *       transaction belongs to this caller", so that check lives with the data.</li>
 * </ol>
 * Each layer catches what the one above cannot. The URL rules alone would let any
 * authenticated user read any other user's transactions.
 */
@Configuration
@EnableWebSecurity
// Activates @PreAuthorize. Without it those annotations are silently inert -- the
// endpoints would still respond, just with no role check whatsoever.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Endpoints reachable without a token: registration, login, docs, health. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // CSRF protects cookie-based sessions, where the browser attaches
                // credentials automatically. This API is stateless and authenticates
                // from an Authorization header that an attacker's page cannot set,
                // so the protection has nothing to defend and only breaks clients.
                .csrf(AbstractHttpConfigurer -> AbstractHttpConfigurer.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // No session is created or consulted: the token is the whole of the
                // authentication state. This is what makes the API horizontally
                // scalable without sticky sessions or a shared session store.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .authorizeHttpRequests(requests -> requests
                        // CORS preflight carries no credentials by design and must be
                        // answered before the browser will send the real request.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // Account administration. Stated here as well as on the
                        // controller so the boundary is visible in one glance, and so
                        // a new admin method that forgets its annotation is still
                        // covered.
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/users/{id:[0-9]+}").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/users/{id:[0-9]+}").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/users/{id:[0-9]+}").hasRole(Role.ADMIN.name())

                        // System-wide financial data. Stated here as well as on the
                        // controller so a new method added to this path without an
                        // annotation is still refused for ordinary accounts.
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/system")
                            .hasAnyRole(Role.ADMIN.name(), Role.MANAGER.name())

                        // Category reference data is readable by anyone signed in;
                        // only administrators may change it.
                        .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole(Role.ADMIN.name())

                        // Everything else -- own profile, transactions, dashboard --
                        // needs authentication. Ownership is then enforced in the
                        // service layer, which is the only place that can see it.
                        .anyRequest().authenticated())

                // Runs before the username/password filter so a bearer token is
                // recognised on requests that carry no form login at all.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .authenticationProvider(authenticationProvider())

                // Suppresses the WWW-Authenticate challenge that would otherwise make
                // browsers show a native Basic-auth dialog against a JSON API.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    /**
     * Verifies email/password pairs at login.
     *
     * <p>{@code hideUserNotFoundExceptions} stays at its default of true so a
     * missing account and a wrong password produce the same failure, denying an
     * attacker a way to test which addresses are registered.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Cross-origin rules for the browser client.
     *
     * <p>Origins come from {@code fintrack.cors.allowed-origins} and default to the
     * Vite dev server. A wildcard is deliberately never used: it cannot be combined
     * with credentials, and one baked in for convenience during development is
     * exactly the kind of thing that survives into production unnoticed. A
     * deployment sets the real origin through the environment instead.
     *
     * <p>Only {@code Authorization}, {@code Content-Type} and {@code Accept} are
     * accepted, and credentials are not enabled -- this API authenticates from a
     * bearer header, never from a cookie, so there is nothing for the browser to
     * attach automatically and therefore no CSRF surface to defend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${fintrack.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // Lets the browser cache the preflight result for an hour instead of
        // repeating an OPTIONS round trip before every request.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

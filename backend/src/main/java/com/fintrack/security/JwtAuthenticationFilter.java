package com.fintrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Turns a valid {@code Authorization: Bearer <token>} header into an authenticated
 * {@code SecurityContext} for the duration of the request.
 *
 * <p>The filter never rejects anything itself. If a token is absent, malformed or
 * expired it simply leaves the context empty and passes the request along; whether
 * that is acceptable is for the authorization rules downstream to decide. That
 * separation is what lets the same filter sit in front of both public and protected
 * endpoints without special-casing either.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // An already-populated context means another mechanism authenticated this
        // request. Overwriting it would be wrong, and re-doing the work is wasteful.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        extractBearerToken(request)
                .flatMap(jwtService::extractUserId)
                .flatMap(this::loadUser)
                .ifPresent(user -> authenticate(user, request));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private Optional<UserDetails> loadUser(Long userId) {
        try {
            UserDetails user = userDetailsService.loadUserById(userId);

            // A token can outlive the account it refers to being suspended or
            // deactivated. The status is therefore re-checked on every request
            // rather than trusted from the moment the token was issued.
            if (!user.isEnabled() || !user.isAccountNonLocked()) {
                log.debug("Rejected JWT for user {}: account is not active", userId);
                return Optional.empty();
            }
            return Optional.of(user);
        } catch (UsernameNotFoundException e) {
            log.debug("Rejected JWT: subject {} does not match any account", userId);
            return Optional.empty();
        }
    }

    private void authenticate(UserDetails user, HttpServletRequest request) {
        // Credentials are null: the bearer token has already been verified, and
        // holding on to it would keep a live credential in memory for no reason.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}

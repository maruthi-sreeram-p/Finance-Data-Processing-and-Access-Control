package com.fintrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.dto.common.ApiError;
import com.fintrack.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders a 403 as JSON when an authenticated caller lacks the required role.
 *
 * <p>Distinct from {@link RestAuthenticationEntryPoint}: 401 means "we do not know
 * who you are", 403 means "we do, and you may not do this". Collapsing them would
 * leave clients unable to tell a token that needs refreshing from a permission they
 * will never have.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        // Warn, not debug: an authenticated caller reaching for something they are
        // not entitled to is worth noticing. The principal id is recorded, but no
        // token and no request body.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? "anonymous" : authentication.getName();
        log.warn("Access denied for principal {} on {} {}",
                principal, request.getMethod(), request.getRequestURI());

        ApiError body = ApiError.of(
                ErrorCode.FORBIDDEN,
                "You do not have permission to perform this operation",
                request.getRequestURI());

        response.setStatus(ErrorCode.FORBIDDEN.statusValue());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

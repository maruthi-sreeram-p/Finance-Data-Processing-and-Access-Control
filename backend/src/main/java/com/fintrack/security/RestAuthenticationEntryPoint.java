package com.fintrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.dto.common.ApiError;
import com.fintrack.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders a 401 as JSON when an unauthenticated request reaches a protected endpoint.
 *
 * <p>Spring Security's default is an HTML login page or a {@code WWW-Authenticate}
 * challenge that makes a browser pop up a Basic-auth dialog. For a token API both
 * are wrong: this is a JSON service, and a client parsing an HTML error page would
 * fail in a way that is needlessly hard to debug.
 *
 * <p>Rejections that happen inside the filter chain never reach
 * {@code GlobalExceptionHandler}, which is why the response has to be written here
 * -- it shares {@link ApiError} so the shape is identical either way.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Debug, not warn: unauthenticated probes are constant background noise on
        // any public API and would otherwise drown the logs.
        log.debug("Unauthenticated request to {} {}", request.getMethod(), request.getRequestURI());

        ApiError body = ApiError.of(
                ErrorCode.UNAUTHORIZED,
                "Authentication is required to access this resource",
                request.getRequestURI());

        response.setStatus(ErrorCode.UNAUTHORIZED.statusValue());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

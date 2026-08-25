package com.fintrack.controller;

import com.fintrack.dto.auth.AuthResponse;
import com.fintrack.dto.auth.LoginRequest;
import com.fintrack.dto.auth.RegisterRequest;
import com.fintrack.dto.common.ApiError;
import com.fintrack.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two unauthenticated entry points into the API.
 *
 * <p>{@code @SecurityRequirements} with no arguments clears the global bearer
 * requirement for this controller, so Swagger UI does not demand a token before
 * letting anyone call the endpoints that exist to obtain one.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Authentication", description = "Registration and login. These endpoints are public.")
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new account",
            description = "Creates an account with the USER role and returns a token for it. "
                    + "The role cannot be chosen by the caller; only an administrator can change it afterwards."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created and token issued"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // 201, not 200: a new resource was created.
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = "Exchanges email and password for a JWT. "
                    + "Every failure returns the same 401 so the endpoint cannot be used to discover "
                    + "which addresses are registered."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or inactive account",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // 200, not 201: logging in creates no resource, it exchanges credentials.
        return ResponseEntity.ok(authenticationService.login(request));
    }
}

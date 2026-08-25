package com.fintrack.controller;

import com.fintrack.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The generated OpenAPI document.
 *
 * <p>Worth testing because the document is produced at runtime from annotations and
 * from the security configuration. A renamed endpoint, a security scheme that stops
 * being registered, or a docs path that stops being public all break silently --
 * nothing fails to compile, and the only symptom is a Swagger UI that no longer
 * works when somebody eventually opens it.
 */
class OpenApiDocumentationTest extends AbstractIntegrationTest {

    private static final String API_DOCS = "/v3/api-docs";

    @Test
    @DisplayName("serves the document without authentication")
    void documentIsPublic() throws Exception {
        // If this needed a token, the page whose purpose is to help you obtain one
        // would be unreachable.
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("FinTrack API"));
    }

    @Test
    @DisplayName("declares the JWT bearer scheme so Swagger UI can authorize")
    void declaresBearerScheme() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    @DisplayName("documents every endpoint group")
    void documentsAllEndpointGroups() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/transactions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/transactions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/transactions/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/transactions/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/transactions/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard'].get").exists())
                .andExpect(jsonPath("$.paths['/api/categories'].get").exists());
    }

    @Test
    @DisplayName("marks the login and register endpoints as needing no token")
    void authEndpointsRequireNoToken() throws Exception {
        // An empty security array on the operation overrides the global requirement.
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty());
    }

    @Test
    @DisplayName("applies the bearer requirement to protected endpoints")
    void protectedEndpointsRequireToken() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/transactions'].get.security").doesNotExist());
    }

    @Test
    @DisplayName("publishes the request and response schemas, including the error shape")
    void publishesSchemas() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest").exists())
                .andExpect(jsonPath("$.components.schemas.AuthResponse").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionRequest").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionResponse").exists())
                .andExpect(jsonPath("$.components.schemas.UserResponse").exists())
                .andExpect(jsonPath("$.components.schemas.DashboardResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError").exists());
    }

    @Test
    @DisplayName("never documents a password field on a response schema")
    void responseSchemasCarryNoPasswordField() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.passwordHash").doesNotExist());
    }
}

package com.fintrack.controller;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The error contract itself.
 *
 * <p>Individual features assert their own status codes elsewhere. What is checked
 * here is the property that spans all of them: that every failure -- whether it came
 * from the security filter chain, from Bean Validation, from a domain rule or from
 * Spring MVC's own dispatch -- arrives in one shape, and that none of them leaks
 * internals.
 */
class ErrorContractIntegrationTest extends AbstractIntegrationTest {

    private String userToken;

    @BeforeEach
    void setUp() {
        User user = persistUser("contract@fintrack.test", Role.USER);
        userToken = tokenFor(user);
    }

    @Test
    @DisplayName("a domain error carries every field of the standard shape")
    void domainErrorHasStandardShape() throws Exception {
        mockMvc.perform(get("/api/transactions/999999").header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/transactions/999999"));
    }

    @Test
    @DisplayName("omits fieldErrors entirely when the failure is not a validation failure")
    void omitsFieldErrorsWhenIrrelevant() throws Exception {
        // Jackson is configured to drop nulls, so a non-validation error carries no
        // vestigial "fieldErrors": null.
        mockMvc.perform(get("/api/transactions/999999").header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("reports every rejected field at once, not one per round trip")
    void reportsAllRejectedFieldsTogether() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "",
                                "email", "not-an-email",
                                "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andReturn();

        var fieldErrors = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("fieldErrors");

        assertThat(fieldErrors).hasSizeGreaterThanOrEqualTo(3);
        assertThat(fieldErrors.toString()).contains("name", "email", "password");
    }

    @Test
    @DisplayName("malformed JSON is a 400 in the standard shape, not a stack trace")
    void handlesMalformedJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"a@b.com\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();

        // Spring's own message for unreadable input can quote the offending payload
        // back to the caller; the handler substitutes a fixed sentence instead.
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("JsonParseException", "com.fasterxml", "at org.springframework");
    }

    @Test
    @DisplayName("an unsupported method is rejected in the standard shape")
    void handlesUnsupportedMethod() throws Exception {
        mockMvc.perform(patch("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("a path variable of the wrong type is a 400, not a 500")
    void handlesTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/transactions/not-a-number").header("Authorization", bearer(userToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("an unparseable enum value is a 400, not a 500")
    void handlesInvalidEnumValue() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", bearer(userToken))
                        .param("type", "SIDEWAYS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("no error response anywhere exposes a stack trace or an exception class")
    void neverLeaksInternals() throws Exception {
        String[] bodies = {
                mockMvc.perform(get("/api/users/me")).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/users").header("Authorization", bearer(userToken)))
                        .andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/transactions/999999").header("Authorization", bearer(userToken)))
                        .andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/transactions/not-a-number").header("Authorization", bearer(userToken)))
                        .andReturn().getResponse().getContentAsString()
        };

        for (String body : bodies) {
            assertThat(body)
                    .doesNotContain("java.lang", "org.springframework", "com.fintrack.exception")
                    .doesNotContain("Exception", "at com.", "\tat ", "stackTrace");
        }
    }

    @Test
    @DisplayName("401 and 403 stay distinct and both arrive in the standard shape")
    void distinguishesUnauthenticatedFromForbidden() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/users"));

        mockMvc.perform(get("/api/users").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }
}

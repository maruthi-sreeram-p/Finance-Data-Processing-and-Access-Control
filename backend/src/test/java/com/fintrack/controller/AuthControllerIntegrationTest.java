package com.fintrack.controller;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.entity.UserStatus;
import com.fintrack.support.AbstractIntegrationTest;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for registration, login and bearer-token authentication.
 *
 * <p>Runs the real filter chain against the real database rather than mocking the
 * service, because the behaviour under test <em>is</em> the wiring: whether the
 * token the API hands out is actually accepted by the filter that guards the other
 * endpoints.
 *
 * <p>Not annotated {@code @Transactional}. Registration deliberately provokes a
 * constraint violation to test the duplicate path, which would poison a shared test
 * transaction; committing for real and cleaning up between tests keeps each case
 * independent and closer to production behaviour.
 */
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ME_URL = "/api/users/me";

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("creates the account and returns a usable token")
        void registersSuccessfully() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "Abhiram P",
                                    "email", "abhiram@example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.user.email").value("abhiram@example.com"))
                    .andExpect(jsonPath("$.user.role").value("USER"))
                    .andExpect(jsonPath("$.user.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("stores a hash, never the password itself")
        void storesOnlyAHash() throws Exception {
            register("hash@example.com", "Passw0rd123");

            User stored = userRepository.findByEmail("hash@example.com").orElseThrow();

            assertThat(stored.getPasswordHash()).isNotEqualTo("Passw0rd123");
            assertThat(stored.getPasswordHash()).startsWith("$2a$12$");
            assertThat(passwordEncoder.matches("Passw0rd123", stored.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("never exposes the password hash in the response body")
        void doesNotLeakHashInResponse() throws Exception {
            MvcResult result = mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "Leak Check",
                                    "email", "leak@example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isCreated())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("password", "passwordHash", "$2a$");
        }

        @Test
        @DisplayName("ignores a role supplied by the caller and always grants USER")
        void refusesCallerSuppliedRole() throws Exception {
            // The privilege-escalation attempt: RegisterRequest has no role component,
            // so the extra property is discarded rather than bound.
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "Would Be Admin",
                                    "email", "escalate@example.com",
                                    "password", "Passw0rd123",
                                    "role", "ADMIN",
                                    "status", "ACTIVE"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.role").value("USER"));

            assertThat(userRepository.findByEmail("escalate@example.com").orElseThrow().getRole())
                    .isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("normalizes the email so casing cannot create a second account")
        void normalizesEmail() throws Exception {
            register("Mixed.Case@Example.COM", "Passw0rd123");

            assertThat(userRepository.findByEmail("mixed.case@example.com")).isPresent();

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "Duplicate",
                                    "email", "mixed.case@EXAMPLE.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("rejects an email that is already registered with 409")
        void rejectsDuplicateEmail() throws Exception {
            register("taken@example.com", "Passw0rd123");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "Second",
                                    "email", "taken@example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
        }

        @Test
        @DisplayName("rejects a malformed email with 400")
        void rejectsMalformedEmail() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "Bad Email",
                                    "email", "not-an-email",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rejects a password with no digit")
        void rejectsWeakPassword() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "Weak Password",
                                    "email", "weak@example.com",
                                    "password", "onlyletters"))))
                    .andExpect(status().isBadRequest());

            assertThat(userRepository.findByEmail("weak@example.com")).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "  ",
                                    "email", "blank@example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns a token for valid credentials")
        void logsInSuccessfully() throws Exception {
            register("login@example.com", "Passw0rd123");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "login@example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("login@example.com"));
        }

        @Test
        @DisplayName("accepts the email in any casing")
        void logsInRegardlessOfEmailCasing() throws Exception {
            register("casing@example.com", "Passw0rd123");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "CASING@Example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("reports a wrong password and an unknown email identically")
        void doesNotRevealWhichAddressesExist() throws Exception {
            register("real@example.com", "Passw0rd123");

            String wrongPassword = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "real@example.com",
                                    "password", "WrongPassw0rd"))))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            String unknownEmail = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "ghost@example.com",
                                    "password", "WrongPassw0rd"))))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            // Identical messages: any difference would turn login into a way to test
            // which addresses are registered.
            assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownEmail));
            assertThat(messageOf(wrongPassword)).isEqualTo("Invalid email or password");
        }

        @Test
        @DisplayName("refuses a suspended account")
        void refusesSuspendedAccount() throws Exception {
            User suspended = TestDataFactory.user("suspended@example.com");
            suspended.setPasswordHash(passwordEncoder.encode("Passw0rd123"));
            suspended.setStatus(UserStatus.SUSPENDED);
            userRepository.save(suspended);

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "suspended@example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("refuses a deactivated account")
        void refusesInactiveAccount() throws Exception {
            User inactive = TestDataFactory.user("inactive@example.com");
            inactive.setPasswordHash(passwordEncoder.encode("Passw0rd123"));
            inactive.setStatus(UserStatus.INACTIVE);
            userRepository.save(inactive);

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "inactive@example.com",
                                    "password", "Passw0rd123"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("bearer token")
    class BearerToken {

        @Test
        @DisplayName("grants access to a protected endpoint")
        void tokenGrantsAccess() throws Exception {
            String token = register("bearer@example.com", "Passw0rd123");

            mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("bearer@example.com"));
        }

        @Test
        @DisplayName("rejects a request with no token as 401 JSON, not an HTML login page")
        void rejectsMissingToken() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.path").value(ME_URL))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        @DisplayName("rejects a token with a tampered signature")
        void rejectsTamperedToken() throws Exception {
            String token = register("tamper@example.com", "Passw0rd123");
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "forgedsignature";

            mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + tampered))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rejects a syntactically invalid token")
        void rejectsGarbageToken() throws Exception {
            mockMvc.perform(get(ME_URL).header("Authorization", "Bearer not.a.jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ignores an Authorization header that is not a Bearer scheme")
        void ignoresNonBearerScheme() throws Exception {
            mockMvc.perform(get(ME_URL).header("Authorization", "Basic dXNlcjpwYXNz"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("stops honouring a token once the account is suspended")
        void tokenStopsWorkingAfterSuspension() throws Exception {
            String token = register("revoked@example.com", "Passw0rd123");

            mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            User user = userRepository.findByEmail("revoked@example.com").orElseThrow();
            user.setStatus(UserStatus.SUSPENDED);
            userRepository.save(user);

            // The token is still cryptographically valid and unexpired. It is refused
            // because the account state is re-read on every request rather than
            // trusted from the moment of issue -- so a suspension takes effect at once.
            mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --- Helpers -------------------------------------------------------------

    private String register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Test User",
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String messageOf(String responseBody) throws Exception {
        return jsonText(responseBody, "message");
    }
}

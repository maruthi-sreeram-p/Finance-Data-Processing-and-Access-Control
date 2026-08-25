package com.fintrack.security;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.entity.UserStatus;
import com.fintrack.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access control on the account endpoints, exercised through the real
 * filter chain with real tokens.
 *
 * <p>Real tokens rather than {@code @WithMockUser}: the annotation injects an
 * authentication directly into the context and would pass even if the JWT filter
 * were broken or missing. These tests fail if any link in the chain breaks.
 */
class UserEndpointAuthorizationTest extends AbstractIntegrationTest {

    private User admin;
    private User manager;
    private User plainUser;

    private String adminToken;
    private String managerToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        admin = persistUser("admin@fintrack.test", Role.ADMIN);
        manager = persistUser("manager@fintrack.test", Role.MANAGER);
        plainUser = persistUser("user@fintrack.test", Role.USER);

        adminToken = tokenFor(admin);
        managerToken = tokenFor(manager);
        userToken = tokenFor(plainUser);
    }

    // --- Listing accounts ----------------------------------------------------

    @Test
    @DisplayName("ADMIN may list accounts")
    void adminMayListAccounts() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("USER may not list accounts")
    void userMayNotListAccounts() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("MANAGER may not list accounts: user administration is ADMIN only")
    void managerMayNotListAccounts() throws Exception {
        // Cross-user read privilege applies to the ledger, not to account records.
        mockMvc.perform(get("/api/users").header("Authorization", bearer(managerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated request is 401, not 403")
    void unauthenticatedIsUnauthorized() throws Exception {
        // 401 and 403 must stay distinct: one means "log in", the other means
        // "logging in will not help".
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // --- Reading and modifying another account -------------------------------

    @Test
    @DisplayName("USER may not read another account by id")
    void userMayNotReadAnotherAccount() throws Exception {
        mockMvc.perform(get("/api/users/" + admin.getId()).header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER may not change another account, which is where role escalation would happen")
    void userMayNotChangeAnotherAccount() throws Exception {
        mockMvc.perform(put("/api/users/" + plainUser.getId())
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden());

        // Even targeting their own id through the admin route must not work.
        assertThat(userRepository.findById(plainUser.getId()).orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("USER may not deactivate another account")
    void userMayNotDeactivateAnotherAccount() throws Exception {
        mockMvc.perform(delete("/api/users/" + manager.getId()).header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(manager.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("ADMIN may change another account's role")
    void adminMayChangeRole() throws Exception {
        mockMvc.perform(put("/api/users/" + plainUser.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    @DisplayName("ADMIN gets 404, not 403, for an account id that does not exist")
    void adminGetsNotFoundForMissingAccount() throws Exception {
        mockMvc.perform(get("/api/users/999999").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    // --- Self-service is open to every role ----------------------------------

    @Test
    @DisplayName("every authenticated role may read its own profile")
    void everyRoleMayReadOwnProfile() throws Exception {
        for (String token : new String[]{adminToken, managerToken, userToken}) {
            mockMvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a user cannot escalate their role through the self-service endpoint")
    void selfServiceCannotEscalateRole() throws Exception {
        // UpdateUserRequest has no role component, so the property is discarded
        // rather than bound. This is the mass-assignment case.
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Still A User",
                                "role", "ADMIN",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        assertThat(userRepository.findById(plainUser.getId()).orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("refuses to deactivate the only remaining administrator")
    void protectsTheLastAdministrator() throws Exception {
        mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("only active administrator")));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("an administrator cannot deactivate themselves through the admin route")
    void adminCannotSelfTargetThroughAdminRoute() throws Exception {
        persistUser("second-admin@fintrack.test", Role.ADMIN);

        mockMvc.perform(delete("/api/users/" + admin.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("/api/users/me")));
    }

}

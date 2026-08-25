package com.fintrack.controller;

import com.fintrack.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The health endpoint used by the container health check.
 *
 * <p>Both properties below are load-bearing for deployment. If the endpoint stopped
 * being public, every probe would see 401 and an orchestrator would restart a
 * perfectly healthy container in a loop. If it started reporting component detail,
 * an unauthenticated caller would learn about the internals.
 */
class HealthEndpointTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("answers without authentication so a container probe can reach it")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("reports only the aggregate status, never component detail")
    void healthHidesComponentDetail() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("exposes no other actuator endpoint, to anonymous or authenticated callers alike")
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        // env and configprops would disclose the datasource URL and, depending on
        // sanitization, far more besides.
        String token = tokenFor(persistUser("probe@fintrack.test", com.fintrack.entity.Role.USER));

        for (String endpoint : new String[]{"/actuator/env", "/actuator/configprops", "/actuator/beans"}) {
            // Anonymous: the security chain rejects before dispatch, so this is 401
            // rather than the 404 a missing handler would give. Two independent
            // reasons the endpoint is unreachable, and this is the outer one.
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized());

            // Authenticated: past the security chain, and now the inner reason shows
            // -- the endpoint was never registered, because only health is included.
            mockMvc.perform(get(endpoint).header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());
        }
    }
}

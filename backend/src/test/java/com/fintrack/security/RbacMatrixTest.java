package com.fintrack.security;

import com.fintrack.entity.Category;
import com.fintrack.entity.Role;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.support.AbstractIntegrationTest;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role-based access control matrix, in one place.
 *
 * <p>The individual feature suites each verify their own corner of authorization.
 * This class exists so the whole policy can be read and audited as a single
 * document: every role against every class of operation, with the reason each
 * answer is what it is.
 *
 * <p>Every request goes through the real filter chain carrying a real JWT minted by
 * {@code JwtService}. {@code @WithMockUser} is deliberately not used anywhere here:
 * it injects an {@code Authentication} straight into the context, which means these
 * tests would keep passing even if the JWT filter were deleted outright.
 *
 * <p>Alice owns a transaction throughout, and Bob is a second ordinary user. Without
 * a second user owning real data, no test in this file could distinguish a working
 * ownership check from a missing one.
 */
class RbacMatrixTest extends AbstractIntegrationTest {

    private User alice;
    private User bob;
    private User manager;
    private User admin;

    private String aliceToken;
    private String bobToken;
    private String managerToken;
    private String adminToken;

    /** Owned by Alice. Every cross-user assertion below targets this record. */
    private Transaction aliceTransaction;

    @BeforeEach
    void setUpRoles() {
        alice = persistUser("alice@fintrack.test", Role.USER);
        bob = persistUser("bob@fintrack.test", Role.USER);
        manager = persistUser("manager@fintrack.test", Role.MANAGER);
        admin = persistUser("admin@fintrack.test", Role.ADMIN);

        aliceToken = tokenFor(alice);
        bobToken = tokenFor(bob);
        managerToken = tokenFor(manager);
        adminToken = tokenFor(admin);

        Category rent = categoryRepository.findByNameIgnoreCase("Rent").orElseThrow();
        aliceTransaction = transactionRepository.save(
                TestDataFactory.expense(alice, rent, "1200.00"));

        Category salary = categoryRepository.findByNameIgnoreCase("Salary").orElseThrow();
        Transaction income = TestDataFactory.transaction(alice, salary, TransactionType.INCOME, "5000.00");
        income.setTransactionDate(LocalDate.of(2026, 8, 20));
        transactionRepository.save(income);
    }

    // =========================================================================
    //  1. Authorities: the wiring everything else depends on
    // =========================================================================

    @Nested
    @DisplayName("granted authorities")
    class Authorities {

        @Test
        @DisplayName("a role maps to exactly one ROLE_-prefixed authority")
        void rolesMapToPrefixedAuthorities() {
            // hasRole('ADMIN') matches the authority ROLE_ADMIN, so the prefix must be
            // present on the authority and absent from the expression. Getting this
            // wrong produces ROLE_ROLE_ADMIN and silently denies everything.
            assertThat(Role.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
            assertThat(Role.MANAGER.authority()).isEqualTo("ROLE_MANAGER");
            assertThat(Role.USER.authority()).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("the principal carries the account's role and nothing else")
        void principalCarriesSingleAuthority() {
            AuthenticatedUser principal = AuthenticatedUser.from(manager);

            assertThat(principal.getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("ROLE_MANAGER");
        }

        @Test
        @DisplayName("authorities come from the database, not from the token's role claim")
        void authorityFollowsTheDatabaseNotTheToken() throws Exception {
            // Bob's token is minted while he is a USER.
            String tokenIssuedAsUser = tokenFor(bob);

            mockMvc.perform(get("/api/dashboard/system").header("Authorization", bearer(tokenIssuedAsUser)))
                    .andExpect(status().isForbidden());

            // An administrator promotes him. The token is untouched and still says USER.
            bob.setRole(Role.MANAGER);
            userRepository.save(bob);

            // The same token now grants manager access, because the filter re-reads the
            // account on every request rather than trusting the claim it was issued with.
            // This is also what makes a demotion or suspension take effect immediately.
            mockMvc.perform(get("/api/dashboard/system").header("Authorization", bearer(tokenIssuedAsUser)))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    //  2. Unauthenticated access  (mandatory case 10)
    // =========================================================================

    @Test
    @DisplayName("10. an unauthenticated caller reaches no protected endpoint")
    void unauthenticatedIsRefusedEverywhere() throws Exception {
        String[] protectedPaths = {
                "/api/users",
                "/api/users/me",
                "/api/transactions",
                "/api/transactions/" + aliceTransaction.getId(),
                "/api/dashboard",
                "/api/dashboard/system",
                "/api/categories",
        };

        for (String path : protectedPaths) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        // Writes too, not only reads.
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    //  3. Resource ownership  (mandatory cases 1 and 2)
    // =========================================================================

    @Nested
    @DisplayName("resource ownership")
    class Ownership {

        @Test
        @DisplayName("1. a user reads their own transaction")
        void ownerMayRead() throws Exception {
            mockMvc.perform(get("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(aliceTransaction.getId()))
                    .andExpect(jsonPath("$.userId").value(alice.getId()));
        }

        @Test
        @DisplayName("2. a user cannot read another user's transaction")
        void nonOwnerMayNotRead() throws Exception {
            mockMvc.perform(get("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(bobToken)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("2a. a user cannot update another user's transaction")
        void nonOwnerMayNotUpdate() throws Exception {
            Category food = categoryRepository.findByNameIgnoreCase("Food").orElseThrow();

            mockMvc.perform(put("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(bobToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "type", "EXPENSE",
                                    "amount", 1,
                                    "categoryId", food.getId(),
                                    "transactionDate", "2026-08-01"))))
                    .andExpect(status().isNotFound());

            assertThat(transactionRepository.findById(aliceTransaction.getId()).orElseThrow().getAmount())
                    .isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("2b. a user cannot delete another user's transaction")
        void nonOwnerMayNotDelete() throws Exception {
            mockMvc.perform(delete("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(bobToken)))
                    .andExpect(status().isNotFound());

            assertThat(transactionRepository.existsById(aliceTransaction.getId())).isTrue();
        }

        @Test
        @DisplayName("a foreign record is indistinguishable from a missing one")
        void foreignAndMissingAreIdentical() throws Exception {
            String foreign = mockMvc.perform(get("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(bobToken)))
                    .andReturn().getResponse().getContentAsString();

            String missing = mockMvc.perform(get("/api/transactions/999999")
                            .header("Authorization", bearer(bobToken)))
                    .andReturn().getResponse().getContentAsString();

            // Answering 403 for a foreign record would confirm the id is real and let
            // an attacker enumerate the table. Both answers carry the same code and
            // status; only the echoed id differs.
            assertThat(jsonText(foreign, "error")).isEqualTo(jsonText(missing, "error"));
            assertThat(jsonInt(foreign, "status")).isEqualTo(jsonInt(missing, "status"));
        }

        @Test
        @DisplayName("the owner is taken from the token, never from the request body")
        void ownerComesFromTheSecurityContext() throws Exception {
            Category rent = categoryRepository.findByNameIgnoreCase("Rent").orElseThrow();

            // The escalation attempt: Bob filing an entry into Alice's ledger.
            mockMvc.perform(post("/api/transactions")
                            .header("Authorization", bearer(bobToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "type", "EXPENSE",
                                    "amount", 99,
                                    "categoryId", rent.getId(),
                                    "transactionDate", "2026-08-01",
                                    "userId", alice.getId()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(bob.getId()));

            // Alice's ledger is untouched: she still has only the two seeded entries.
            assertThat(transactionRepository.countByUserId(alice.getId())).isEqualTo(2);
        }
    }

    // =========================================================================
    //  4. USER restrictions  (mandatory cases 3, 7, 8)
    // =========================================================================

    @Nested
    @DisplayName("USER role")
    class UserRole {

        @Test
        @DisplayName("3. cannot reach any admin endpoint")
        void mayNotReachAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/users").header("Authorization", bearer(bobToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));

            mockMvc.perform(get("/api/users/" + alice.getId()).header("Authorization", bearer(bobToken)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/users/" + alice.getId()).header("Authorization", bearer(bobToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("3a. cannot administer categories")
        void mayNotAdministerCategories() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .header("Authorization", bearer(bobToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Sneaky", "type", "EXPENSE"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("3b. cannot read system-wide financial data")
        void mayNotReadSystemDashboard() throws Exception {
            mockMvc.perform(get("/api/dashboard/system").header("Authorization", bearer(bobToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("7. cannot change their own role through the admin route")
        void mayNotChangeOwnRoleViaAdminRoute() throws Exception {
            mockMvc.perform(put("/api/users/" + bob.getId())
                            .header("Authorization", bearer(bobToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                    .andExpect(status().isForbidden());

            assertThat(userRepository.findById(bob.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("8. cannot assign themselves ADMIN through the self-service route")
        void mayNotEscalateViaSelfService() throws Exception {
            // The mass-assignment attempt. UpdateUserRequest has no role component, so
            // the property is discarded during binding rather than rejected -- which is
            // why the request succeeds while the role does not change.
            mockMvc.perform(put("/api/users/me")
                            .header("Authorization", bearer(bobToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Bob Still A User",
                                    "role", "ADMIN",
                                    "status", "ACTIVE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"));

            assertThat(userRepository.findById(bob.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("cannot page another user's ledger by passing ?userId=")
        void mayNotTargetAnotherLedger() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .header("Authorization", bearer(bobToken))
                            .param("userId", String.valueOf(alice.getId())))
                    .andExpect(status().isOk())
                    // Pinned to his own ledger, which is empty. The parameter is ignored
                    // rather than rejected, so it cannot be used to probe for live accounts.
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("cannot read another user's dashboard by passing ?userId=")
        void mayNotTargetAnotherDashboard() throws Exception {
            mockMvc.perform(get("/api/dashboard")
                            .header("Authorization", bearer(bobToken))
                            .param("userId", String.valueOf(alice.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(bob.getId()))
                    // Alice's 5000 income must not appear.
                    .andExpect(jsonPath("$.totalIncome").value(0));
        }

        @Test
        @DisplayName("may fully manage their own data")
        void mayManageOwnData() throws Exception {
            mockMvc.perform(get("/api/users/me").header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/transactions").header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }
    }

    // =========================================================================
    //  5. MANAGER  (mandatory cases 4 and 5)
    // =========================================================================

    @Nested
    @DisplayName("MANAGER role")
    class ManagerRole {

        @Test
        @DisplayName("4. may read the organization-wide dashboard")
        void mayReadSystemDashboard() throws Exception {
            mockMvc.perform(get("/api/dashboard/system").header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope").value("SYSTEM"))
                    // Alice's two entries, aggregated across every account.
                    .andExpect(jsonPath("$.totalIncome").value(5000.00))
                    .andExpect(jsonPath("$.totalExpenses").value(1200.00))
                    .andExpect(jsonPath("$.transactionCount").value(2))
                    .andExpect(jsonPath("$.activeLedgerCount").value(1));
        }

        @Test
        @DisplayName("4a. may read an individual ledger and dashboard")
        void mayReadAnyLedger() throws Exception {
            mockMvc.perform(get("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/dashboard")
                            .header("Authorization", bearer(managerToken))
                            .param("userId", String.valueOf(alice.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(alice.getId()));
        }

        @Test
        @DisplayName("5. may not perform administrative operations")
        void mayNotAdminister() throws Exception {
            mockMvc.perform(get("/api/users").header("Authorization", bearer(managerToken)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/users/" + alice.getId()).header("Authorization", bearer(managerToken)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/users/" + alice.getId()).header("Authorization", bearer(managerToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5a. may not change anyone's role, including an administrator's")
        void mayNotChangeRoles() throws Exception {
            mockMvc.perform(put("/api/users/" + admin.getId())
                            .header("Authorization", bearer(managerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                    .andExpect(status().isForbidden());

            assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        }

        @Test
        @DisplayName("5b. may not escalate itself to ADMIN")
        void mayNotSelfEscalate() throws Exception {
            mockMvc.perform(put("/api/users/" + manager.getId())
                            .header("Authorization", bearer(managerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                    .andExpect(status().isForbidden());

            assertThat(userRepository.findById(manager.getId()).orElseThrow().getRole())
                    .isEqualTo(Role.MANAGER);
        }

        @Test
        @DisplayName("read privilege does not extend to writing another user's records")
        void mayNotWriteAnotherLedger() throws Exception {
            mockMvc.perform(delete("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isNotFound());

            assertThat(transactionRepository.existsById(aliceTransaction.getId())).isTrue();
        }

        @Test
        @DisplayName("may not administer categories")
        void mayNotAdministerCategories() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .header("Authorization", bearer(managerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Manager Category", "type", "EXPENSE"))))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    //  6. ADMIN  (mandatory cases 6 and 9)
    // =========================================================================

    @Nested
    @DisplayName("ADMIN role")
    class AdminRole {

        @Test
        @DisplayName("6. may reach the admin endpoints")
        void mayReachAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/users").header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4));

            mockMvc.perform(get("/api/users/" + alice.getId()).header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/dashboard/system").header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scope").value("SYSTEM"));
        }

        @Test
        @DisplayName("9. may change a user's role")
        void mayManageRoles() throws Exception {
            mockMvc.perform(put("/api/users/" + bob.getId())
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("role", "MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MANAGER"));

            assertThat(userRepository.findById(bob.getId()).orElseThrow().getRole()).isEqualTo(Role.MANAGER);
        }

        @Test
        @DisplayName("9a. may suspend and deactivate accounts")
        void mayManageAccountStatus() throws Exception {
            mockMvc.perform(put("/api/users/" + bob.getId())
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUSPENDED"));

            // A suspended account's existing token stops working immediately, because
            // the filter re-reads the account rather than trusting the token.
            mockMvc.perform(get("/api/users/me").header("Authorization", bearer(bobToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("may still not rewrite another user's financial records")
        void mayNotWriteAnotherLedger() throws Exception {
            // The deliberate asymmetry: read widens with privilege, write never does.
            // A ledger a third party can silently alter is not trustworthy, and no
            // support task requires it.
            mockMvc.perform(delete("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isNotFound());

            Category food = categoryRepository.findByNameIgnoreCase("Food").orElseThrow();
            mockMvc.perform(put("/api/transactions/" + aliceTransaction.getId())
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "type", "EXPENSE",
                                    "amount", 1,
                                    "categoryId", food.getId(),
                                    "transactionDate", "2026-08-01"))))
                    .andExpect(status().isNotFound());

            assertThat(transactionRepository.findById(aliceTransaction.getId()).orElseThrow().getAmount())
                    .isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("cannot remove the last remaining administrator")
        void protectsTheLastAdministrator() throws Exception {
            // Otherwise user administration becomes permanently unreachable, fixable
            // only by editing the database by hand.
            mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(adminToken)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/users/" + admin.getId())
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                    .andExpect(status().isForbidden());
        }
    }
}

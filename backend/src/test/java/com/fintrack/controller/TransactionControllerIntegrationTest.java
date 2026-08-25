package com.fintrack.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transaction CRUD, filtering, pagination and -- above all -- ownership.
 *
 * <p>Alice and Bob each own records throughout, so every request has a wrong answer
 * available to it. A test that only ever exercises one user's data cannot detect a
 * missing ownership check.
 */
class TransactionControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String TRANSACTIONS_URL = "/api/transactions";

    private User alice;
    private User bob;
    private User admin;
    private User manager;

    private String aliceToken;
    private String bobToken;
    private String adminToken;
    private String managerToken;

    private Category rent;
    private Category food;
    private Category salary;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice@fintrack.test", Role.USER);
        bob = persistUser("bob@fintrack.test", Role.USER);
        admin = persistUser("admin@fintrack.test", Role.ADMIN);
        manager = persistUser("manager@fintrack.test", Role.MANAGER);

        aliceToken = tokenFor(alice);
        bobToken = tokenFor(bob);
        adminToken = tokenFor(admin);
        managerToken = tokenFor(manager);

        rent = category("Rent");
        food = category("Food");
        salary = category("Salary");
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates an expense owned by the caller and returns 201 with a Location header")
        void createsExpense() throws Exception {
            mockMvc.perform(post(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(TransactionType.EXPENSE, "1250.00", rent.getId(),
                                    "August rent", LocalDate.of(2026, 8, 1))))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.userId").value(alice.getId()))
                    .andExpect(jsonPath("$.amount").value(1250.00))
                    .andExpect(jsonPath("$.category.name").value("Rent"))
                    .andExpect(jsonPath("$.type").value("EXPENSE"));
        }

        @Test
        @DisplayName("ignores an attempt to file the entry into someone else's ledger")
        void cannotCreateInAnotherUsersLedger() throws Exception {
            Map<String, Object> body = transactionMap(TransactionType.EXPENSE, "500.00", rent.getId(),
                    "Not yours", LocalDate.of(2026, 8, 1));
            // TransactionRequest has no userId component, so this is discarded rather
            // than bound -- the owner always comes from the security context.
            body.put("userId", bob.getId());

            mockMvc.perform(post(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(alice.getId()));

            assertThat(transactionRepository.countByUserId(bob.getId())).isZero();
        }

        @Test
        @DisplayName("refuses an expense filed under an income-only category")
        void refusesIncompatibleCategory() throws Exception {
            // The rule Bean Validation cannot express: it spans the request and the
            // categories table. Without it, dashboard aggregates would silently rot.
            mockMvc.perform(post(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(TransactionType.EXPENSE, "100.00", salary.getId(),
                                    "Miscategorised", LocalDate.of(2026, 8, 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("cannot be used for EXPENSE")));
        }

        @Test
        @DisplayName("accepts a BOTH category for either direction")
        void acceptsBothCategoryEitherWay() throws Exception {
            Category investment = category("Investment");

            for (TransactionType type : TransactionType.values()) {
                mockMvc.perform(post(TRANSACTIONS_URL)
                                .header("Authorization", bearer(aliceToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transactionJson(type, "250.00", investment.getId(),
                                        "Investment", LocalDate.of(2026, 8, 1))))
                        .andExpect(status().isCreated());
            }
        }

        @Test
        @DisplayName("rejects a zero or negative amount")
        void rejectsNonPositiveAmount() throws Exception {
            for (String amount : new String[]{"0.00", "-25.00"}) {
                mockMvc.perform(post(TRANSACTIONS_URL)
                                .header("Authorization", bearer(aliceToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transactionJson(TransactionType.EXPENSE, amount, rent.getId(),
                                        "Invalid", LocalDate.of(2026, 8, 1))))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
            }
        }

        @Test
        @DisplayName("rejects a future-dated entry")
        void rejectsFutureDate() throws Exception {
            // A ledger records money that has already moved; a future entry would
            // silently distort the current balance.
            mockMvc.perform(post(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(TransactionType.EXPENSE, "100.00", rent.getId(),
                                    "Next year", LocalDate.now().plusDays(1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("transactionDate"));
        }

        @Test
        @DisplayName("reports an unknown category as 404")
        void reportsUnknownCategory() throws Exception {
            mockMvc.perform(post(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(TransactionType.EXPENSE, "100.00", 999999L,
                                    "Ghost category", LocalDate.of(2026, 8, 1))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("requires authentication")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post(TRANSACTIONS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(TransactionType.EXPENSE, "100.00", rent.getId(),
                                    "Anonymous", LocalDate.of(2026, 8, 1))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("a user reads their own transaction")
        void readsOwnTransaction() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            mockMvc.perform(get(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(aliceEntry.getId()));
        }

        @Test
        @DisplayName("changing the id in the URL to another user's record returns 404, not the record")
        void cannotReadAnotherUsersTransaction() throws Exception {
            // The scenario the specification calls out: Bob asking for /api/transactions/{aliceId}.
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            mockMvc.perform(get(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(bobToken)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("a foreign record and a missing one are indistinguishable")
        void foreignAndMissingLookIdentical() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            String foreign = mockMvc.perform(get(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(bobToken)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            String missing = mockMvc.perform(get(TRANSACTIONS_URL + "/999999")
                            .header("Authorization", bearer(bobToken)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            // Only the id differs. If a foreign record answered 403 while a missing one
            // answered 404, an attacker could walk the id space to learn which exist.
            assertThat(errorOf(foreign)).isEqualTo(errorOf(missing));
            assertThat(statusOf(foreign)).isEqualTo(statusOf(missing));
        }

        @Test
        @DisplayName("a user cannot update another user's transaction")
        void cannotUpdateAnotherUsersTransaction() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            mockMvc.perform(put(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(bobToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(TransactionType.EXPENSE, "1.00", food.getId(),
                                    "Hijacked", LocalDate.of(2026, 8, 1))))
                    .andExpect(status().isNotFound());

            Transaction unchanged = transactionRepository.findById(aliceEntry.getId()).orElseThrow();
            assertThat(unchanged.getAmount()).isEqualByComparingTo("1200.00");
            assertThat(unchanged.getDescription()).isNotEqualTo("Hijacked");
        }

        @Test
        @DisplayName("a user cannot delete another user's transaction")
        void cannotDeleteAnotherUsersTransaction() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            mockMvc.perform(delete(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(bobToken)))
                    .andExpect(status().isNotFound());

            assertThat(transactionRepository.existsById(aliceEntry.getId())).isTrue();
        }

        @Test
        @DisplayName("an update cannot move a record into another user's ledger")
        void updateCannotReassignOwner() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            Map<String, Object> body = transactionMap(TransactionType.EXPENSE, "1300.00", rent.getId(),
                    "Reassigned", LocalDate.of(2026, 8, 2));
            body.put("userId", bob.getId());

            mockMvc.perform(put(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(aliceToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(alice.getId()));

            assertThat(transactionRepository.countByUserId(bob.getId())).isZero();
        }
    }

    @Nested
    @DisplayName("privileged access")
    class PrivilegedAccess {

        @Test
        @DisplayName("ADMIN may read another user's transaction")
        void adminMayReadAnyTransaction() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            mockMvc.perform(get(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(alice.getId()));
        }

        @Test
        @DisplayName("MANAGER may page another user's ledger with ?userId=")
        void managerMayReadAnotherLedger() throws Exception {
            save(alice, rent, TransactionType.EXPENSE, "1200.00");
            save(alice, food, TransactionType.EXPENSE, "80.00");

            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(managerToken))
                            .param("userId", String.valueOf(alice.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("a plain user asking for another ledger gets their own, not a 403")
        void plainUserIsPinnedToOwnLedger() throws Exception {
            save(alice, rent, TransactionType.EXPENSE, "1200.00");
            save(bob, food, TransactionType.EXPENSE, "80.00");

            // ?userId=alice as Bob. The parameter is ignored rather than rejected, and
            // Bob sees only his own single entry.
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(bobToken))
                            .param("userId", String.valueOf(alice.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].userId").value(bob.getId()));
        }

        @Test
        @DisplayName("ADMIN still cannot modify another user's transaction")
        void adminCannotModifyAnotherUsersTransaction() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            // Read privilege widens with role; write privilege never does.
            mockMvc.perform(put(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson(TransactionType.EXPENSE, "1.00", food.getId(),
                                    "Admin edit", LocalDate.of(2026, 8, 1))))
                    .andExpect(status().isNotFound());

            assertThat(transactionRepository.findById(aliceEntry.getId()).orElseThrow().getAmount())
                    .isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("ADMIN still cannot delete another user's transaction")
        void adminCannotDeleteAnotherUsersTransaction() throws Exception {
            Transaction aliceEntry = save(alice, rent, TransactionType.EXPENSE, "1200.00");

            mockMvc.perform(delete(TRANSACTIONS_URL + "/" + aliceEntry.getId())
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isNotFound());

            assertThat(transactionRepository.existsById(aliceEntry.getId())).isTrue();
        }
    }

    @Nested
    @DisplayName("filtering, sorting and pagination")
    class Filtering {

        @BeforeEach
        void seedLedger() {
            save(alice, rent, TransactionType.EXPENSE, "1200.00", LocalDate.of(2026, 8, 1));
            save(alice, food, TransactionType.EXPENSE, "80.00", LocalDate.of(2026, 8, 15));
            save(alice, food, TransactionType.EXPENSE, "45.50", LocalDate.of(2026, 9, 2));
            save(alice, salary, TransactionType.INCOME, "5000.00", LocalDate.of(2026, 8, 31));
            save(bob, rent, TransactionType.EXPENSE, "900.00", LocalDate.of(2026, 8, 1));
        }

        @Test
        @DisplayName("returns only the caller's own entries by default")
        void listsOnlyOwnLedger() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL).header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4));
        }

        @Test
        @DisplayName("filters by type")
        void filtersByType() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("type", "INCOME"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].amount").value(5000.00));
        }

        @Test
        @DisplayName("filters by category name, case-insensitively")
        void filtersByCategoryName() throws Exception {
            for (String name : new String[]{"Food", "food", "FOOD"}) {
                mockMvc.perform(get(TRANSACTIONS_URL)
                                .header("Authorization", bearer(aliceToken))
                                .param("category", name))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(2));
            }
        }

        @Test
        @DisplayName("returns nothing for an unknown category rather than the whole ledger")
        void unknownCategoryMatchesNothing() throws Exception {
            // Silently dropping the filter would answer a typo with every row, which
            // looks like a working query returning wrong results.
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("category", "Foodd"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("filters by an inclusive date range")
        void filtersByDateRange() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("from", "2026-08-01")
                            .param("to", "2026-08-31"))
                    .andExpect(status().isOk())
                    // Both boundary dates are included: 1 Aug and 31 Aug both count.
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        @DisplayName("filters by amount range")
        void filtersByAmountRange() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("minAmount", "50.00")
                            .param("maxAmount", "1500.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("combines several filters")
        void combinesFilters() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("type", "EXPENSE")
                            .param("category", "Food")
                            .param("from", "2026-08-01")
                            .param("to", "2026-08-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].amount").value(80.00));
        }

        @Test
        @DisplayName("rejects an inverted date range")
        void rejectsInvertedDateRange() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("from", "2026-08-31")
                            .param("to", "2026-08-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION"));
        }

        @Test
        @DisplayName("paginates")
        void paginates() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(false));

            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("page", "1")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.first").value(false))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("sorts by amount descending when asked")
        void sortsByAmount() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("sort", "amount,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].amount").value(5000.00))
                    .andExpect(jsonPath("$.content[3].amount").value(45.50));
        }

        @Test
        @DisplayName("defaults to most recent first")
        void defaultsToMostRecentFirst() throws Exception {
            mockMvc.perform(get(TRANSACTIONS_URL).header("Authorization", bearer(aliceToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].transactionDate").value("2026-09-02"));
        }

        @Test
        @DisplayName("ignores a sort on a property that is not whitelisted")
        void ignoresDisallowedSortProperty() throws Exception {
            // An unknown or sensitive path would otherwise throw, turning a typo into
            // a 500 -- or let a client order by a lazy association.
            mockMvc.perform(get(TRANSACTIONS_URL)
                            .header("Authorization", bearer(aliceToken))
                            .param("sort", "user.passwordHash,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].transactionDate").value("2026-09-02"));
        }
    }

    // --- Helpers -------------------------------------------------------------

    private Category category(String name) {
        return categoryRepository.findByNameIgnoreCase(name).orElseThrow();
    }

    private Transaction save(User owner, Category category, TransactionType type, String amount) {
        return save(owner, category, type, amount, LocalDate.of(2026, 8, 15));
    }

    private Transaction save(User owner, Category category, TransactionType type, String amount, LocalDate date) {
        Transaction transaction = TestDataFactory.transaction(owner, category, type, amount);
        transaction.setTransactionDate(date);
        return transactionRepository.save(transaction);
    }

    private String transactionJson(TransactionType type, String amount, Long categoryId,
                                   String description, LocalDate date) throws Exception {
        return objectMapper.writeValueAsString(transactionMap(type, amount, categoryId, description, date));
    }

    private Map<String, Object> transactionMap(TransactionType type, String amount, Long categoryId,
                                               String description, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type.name());
        body.put("amount", new BigDecimal(amount));
        body.put("categoryId", categoryId);
        body.put("description", description);
        body.put("transactionDate", date.toString());
        return body;
    }

    private String errorOf(String body) throws Exception {
        return jsonText(body, "error");
    }

    private int statusOf(String body) throws Exception {
        return jsonInt(body, "status");
    }
}

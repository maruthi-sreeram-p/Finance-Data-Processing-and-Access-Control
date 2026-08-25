package com.fintrack.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.JwtService;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for tests that drive the API through the real filter chain.
 *
 * <p>These tests are deliberately <em>not</em> {@code @Transactional}. Several of
 * them provoke constraint violations on purpose, which would poison a shared test
 * transaction, and committing for real is closer to how the application actually
 * behaves.
 *
 * <p>The price of committing is that rows outlive the test. The in-memory database
 * is shared across the whole run -- Spring caches application contexts, and the H2
 * URL uses {@code DB_CLOSE_DELAY=-1} -- so a class that leaves accounts behind will
 * break an unrelated {@code @DataJpaTest} that happens to use the same email. The
 * ledger is therefore cleared both before and after every test: before, so a test
 * never inherits state; after, so it never imposes any.
 *
 * <p>Seeded categories are never touched. They are migration-owned reference data
 * that every test expects to be present.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "Passw0rd123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    /**
     * Runs before any subclass setup, since JUnit invokes superclass lifecycle
     * callbacks first.
     */
    @BeforeEach
    void clearLedgerBeforeTest() {
        clearLedger();
    }

    @AfterEach
    void clearLedgerAfterTest() {
        clearLedger();
    }

    /** Children before parents: transactions hold the foreign key to users. */
    private void clearLedger() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Creates an active account with a known password and returns it. */
    protected User persistUser(String email, Role role) {
        User user = TestDataFactory.user(email);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        return userRepository.save(user);
    }

    protected String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Reads a top-level text field out of a JSON response body. */
    protected String jsonText(String responseBody, String field) throws Exception {
        return objectMapper.readTree(responseBody).get(field).asText();
    }

    /** Reads a top-level numeric field out of a JSON response body. */
    protected int jsonInt(String responseBody, String field) throws Exception {
        return objectMapper.readTree(responseBody).get(field).asInt();
    }
}

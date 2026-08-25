package com.fintrack.repository;

import com.fintrack.entity.Category;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.User;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User alice;
    private User bob;
    private Category rent;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(TestDataFactory.user("alice@fintrack.test"));
        bob = userRepository.save(TestDataFactory.user("bob@fintrack.test"));
        rent = categoryRepository.findByNameIgnoreCase("Rent").orElseThrow();
    }

    @Test
    @DisplayName("finds a transaction through its owner")
    void findsOwnTransaction() {
        Transaction saved = transactionRepository.save(TestDataFactory.expense(alice, rent, "1200.00"));

        Optional<Transaction> found = transactionRepository.findByIdAndUserId(saved.getId(), alice.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("hides another user's transaction rather than reporting it as forbidden")
    void doesNotReturnAnotherUsersTransaction() {
        Transaction aliceTransaction = transactionRepository.save(TestDataFactory.expense(alice, rent, "1200.00"));

        // The core horizontal-privilege-escalation guard, enforced in the WHERE clause.
        // Bob asking for Alice's id gets an empty Optional, which the service turns into
        // a 404 -- so the response cannot be used to probe which ids exist.
        Optional<Transaction> found = transactionRepository.findByIdAndUserId(aliceTransaction.getId(), bob.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("stores monetary amounts at exactly two decimal places without drift")
    void preservesMonetaryScale() {
        // 0.1 has no exact binary representation; this is the case a double would fail.
        Transaction saved = transactionRepository.saveAndFlush(TestDataFactory.expense(alice, rent, "0.10"));
        entityManager.clear();

        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAmount()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(reloaded.getAmount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("refuses a non-positive amount at the database level")
    void rejectsNonPositiveAmount() {
        Transaction invalid = TestDataFactory.expense(alice, rent, "0.00");

        // ck_transactions_amount_positive is the last line of defence for any write
        // that reaches the database without passing through Bean Validation.
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("reports whether a category is still referenced by any transaction")
    void detectsCategoryStillInUse() {
        transactionRepository.save(TestDataFactory.expense(alice, rent, "1200.00"));
        Category unused = categoryRepository.findByNameIgnoreCase("Freelancing").orElseThrow();

        assertThat(transactionRepository.existsByCategoryId(rent.getId())).isTrue();
        assertThat(transactionRepository.existsByCategoryId(unused.getId())).isFalse();
    }

    @Test
    @DisplayName("counts only the transactions belonging to the given user")
    void countsPerUser() {
        transactionRepository.save(TestDataFactory.expense(alice, rent, "1200.00"));
        transactionRepository.save(TestDataFactory.expense(alice, rent, "800.00"));
        transactionRepository.save(TestDataFactory.expense(bob, rent, "500.00"));

        assertThat(transactionRepository.countByUserId(alice.getId())).isEqualTo(2);
        assertThat(transactionRepository.countByUserId(bob.getId())).isEqualTo(1);
    }
}

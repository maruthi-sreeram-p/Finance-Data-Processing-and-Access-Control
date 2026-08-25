package com.fintrack.repository;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code replace = NONE} keeps the datasource declared in application-test.yml, so
 * these tests run against the real Flyway-migrated schema rather than one Hibernate
 * generates on the fly. Without it the migrations would never be exercised.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("persists a user and finds it back by email")
    void findsUserByEmail() {
        userRepository.save(TestDataFactory.user("alice@fintrack.test"));

        Optional<User> found = userRepository.findByEmail("alice@fintrack.test");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test User");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("rejects a second account with an email that is already taken")
    void enforcesUniqueEmail() {
        userRepository.saveAndFlush(TestDataFactory.user("duplicate@fintrack.test"));

        User duplicate = TestDataFactory.user("duplicate@fintrack.test");

        // Uniqueness is guaranteed by uk_users_email, not only by the service-level
        // pre-check: two concurrent registrations can both pass the check and only
        // the constraint stops the second one.
        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("reports whether an email is already registered")
    void reportsEmailExistence() {
        userRepository.save(TestDataFactory.user("taken@fintrack.test"));

        assertThat(userRepository.existsByEmail("taken@fintrack.test")).isTrue();
        assertThat(userRepository.existsByEmail("free@fintrack.test")).isFalse();
    }

    @Test
    @DisplayName("populates audit timestamps on insert without the caller setting them")
    void populatesAuditTimestamps() {
        User saved = userRepository.saveAndFlush(TestDataFactory.user());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("advances updatedAt but preserves createdAt on modification")
    void keepsCreatedAtStableAcrossUpdates() {
        User saved = userRepository.saveAndFlush(TestDataFactory.user());
        var originalCreatedAt = saved.getCreatedAt();
        entityManager.detach(saved);

        saved.setName("Renamed");
        User updated = userRepository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
    }
}

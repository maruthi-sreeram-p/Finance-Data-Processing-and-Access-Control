package com.fintrack.util;

import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.Role;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.entity.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builders for valid domain objects in tests.
 *
 * <p>Every factory method returns a fully valid instance, so a test only has to
 * state the one field it actually cares about. That keeps the intent of each test
 * visible instead of buried in eight lines of irrelevant setup.
 */
public final class TestDataFactory {

    /** Guarantees unique emails so tests can be run in any order within one context. */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestDataFactory() {
    }

    public static User user(String email) {
        return User.builder()
                .name("Test User")
                .email(email)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRS")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static User user() {
        return user("user%d@fintrack.test".formatted(SEQUENCE.incrementAndGet()));
    }

    public static User admin() {
        User admin = user("admin%d@fintrack.test".formatted(SEQUENCE.incrementAndGet()));
        admin.setRole(Role.ADMIN);
        return admin;
    }

    public static Category category(String name, CategoryType type) {
        return Category.builder()
                .name(name)
                .description(name + " category")
                .type(type)
                .build();
    }

    public static Transaction transaction(User owner, Category category, TransactionType type, String amount) {
        return Transaction.builder()
                .user(owner)
                .category(category)
                .type(type)
                .amount(new BigDecimal(amount))
                .description("Test transaction")
                .transactionDate(LocalDate.of(2026, 8, 15))
                .build();
    }

    public static Transaction expense(User owner, Category category, String amount) {
        return transaction(owner, category, TransactionType.EXPENSE, amount);
    }

    public static Transaction income(User owner, Category category, String amount) {
        return transaction(owner, category, TransactionType.INCOME, amount);
    }
}

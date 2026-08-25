package com.fintrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

/**
 * A FinTrack account holder.
 *
 * <p>Note what is <em>not</em> here: there is no {@code @OneToMany} collection of
 * transactions. A user can accumulate tens of thousands of them, and mapping the
 * collection would invite {@code user.getTransactions()} calls that load the whole
 * ledger into memory and bypass pagination entirely. The relationship is navigated
 * in one direction only, from {@link Transaction#getUser()}, and queried through
 * {@code TransactionRepository} with a page request.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Unique login identifier, stored normalized to lower case so that
     * {@code Alice@Example.com} and {@code alice@example.com} cannot both register.
     */
    @Column(nullable = false, length = 255)
    private String email;

    /**
     * BCrypt hash of the password -- never the password itself. The column is named
     * {@code password_hash} so that anyone reading the schema, a query log, or a
     * database dump can see at a glance that no plaintext is stored here.
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Identity is the persistent key. Entities without an id are only equal to
     * themselves, which prevents two distinct unsaved users from collapsing into
     * one entry when held in a {@code Set}.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return id != null && Objects.equals(id, unwrapId(user));
    }

    /**
     * Constant hash code: an entity's id is null before the insert and populated
     * after it, so deriving the hash from the id would move the object between
     * buckets mid-session and make it unfindable in a hash-based collection.
     */
    @Override
    public final int hashCode() {
        return User.class.hashCode();
    }

    private static Long unwrapId(User user) {
        return user instanceof HibernateProxy proxy
                ? (Long) proxy.getHibernateLazyInitializer().getIdentifier()
                : user.getId();
    }

    @Override
    public String toString() {
        // Deliberately excludes passwordHash so a stray log statement cannot leak it.
        return "User{id=%d, email='%s', role=%s, status=%s}".formatted(id, email, role, status);
    }
}

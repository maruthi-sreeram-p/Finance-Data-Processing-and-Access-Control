package com.fintrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A single income or expense entry in a user's ledger.
 *
 * <p>Both associations are {@code LAZY}. Eager loading here would be actively
 * harmful: listing a page of 20 transactions would issue 40 extra queries, and the
 * owning {@link User} -- including its password hash -- would be pulled into memory
 * on every read. Queries that genuinely need the category use an explicit fetch
 * join instead.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                // Every user-facing query is scoped by user_id and almost always sorted or
                // filtered by date, so this composite index serves the default listing,
                // the date-range filter and the dashboard aggregates alike.
                @Index(name = "idx_transactions_user_date", columnList = "user_id, transaction_date"),
                @Index(name = "idx_transactions_user_type", columnList = "user_id, type"),
                @Index(name = "idx_transactions_category", columnList = "category_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner of the record. This field is what every ownership check resolves against. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_transactions_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    /**
     * Always stored positive; {@link #type} carries the direction.
     *
     * <p>{@code DECIMAL(19,2)}, never a binary floating-point type: {@code double}
     * cannot represent 0.10 exactly, so summing a few thousand expenses would drift
     * away from the true total by a visible amount.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_transactions_category"))
    private Category category;

    @Column(length = 255)
    private String description;

    /**
     * The date the money actually moved, which is not necessarily the date the row
     * was created -- users backfill. A {@code LocalDate} avoids the timezone traps
     * of a timestamp: a purchase made on the 31st belongs to that month for everyone.
     */
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /**
     * @return the signed contribution of this transaction to a balance
     */
    public BigDecimal signedAmount() {
        return type == TransactionType.EXPENSE ? amount.negate() : amount;
    }

    /**
     * @return whether the given user id owns this transaction
     */
    public boolean isOwnedBy(Long userId) {
        return userId != null && user != null && userId.equals(user.getId());
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transaction transaction)) {
            return false;
        }
        return id != null && Objects.equals(id, unwrapId(transaction));
    }

    @Override
    public final int hashCode() {
        return Transaction.class.hashCode();
    }

    private static Long unwrapId(Transaction transaction) {
        return transaction instanceof HibernateProxy proxy
                ? (Long) proxy.getHibernateLazyInitializer().getIdentifier()
                : transaction.getId();
    }

    @Override
    public String toString() {
        // Touches no lazy association: dereferencing user or category here would
        // throw LazyInitializationException whenever a detached instance is logged.
        return "Transaction{id=%d, type=%s, amount=%s, transactionDate=%s}"
                .formatted(id, type, amount, transactionDate);
    }
}

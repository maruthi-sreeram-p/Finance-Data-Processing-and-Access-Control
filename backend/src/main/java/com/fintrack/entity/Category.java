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
 * A classification bucket for transactions, e.g. "Rent" or "Salary".
 *
 * <p>Categories are shared reference data seeded by migration {@code V2}, not
 * per-user rows, and no code anywhere branches on a category id. Business rules
 * are driven by {@link #type} instead, so an administrator can add "Subscriptions"
 * through the API and it behaves correctly without a code change.
 */
@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_categories_name", columnNames = "name")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * Which transaction directions may use this category. See
     * {@link CategoryType#accepts(TransactionType)}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryType type;

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Category category)) {
            return false;
        }
        return id != null && Objects.equals(id, unwrapId(category));
    }

    @Override
    public final int hashCode() {
        return Category.class.hashCode();
    }

    private static Long unwrapId(Category category) {
        return category instanceof HibernateProxy proxy
                ? (Long) proxy.getHibernateLazyInitializer().getIdentifier()
                : category.getId();
    }

    @Override
    public String toString() {
        return "Category{id=%d, name='%s', type=%s}".formatted(id, name, type);
    }
}

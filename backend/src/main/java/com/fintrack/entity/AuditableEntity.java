package com.fintrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Creation and modification timestamps shared by every persistent entity.
 *
 * <p>The values are written by Spring Data's auditing listener rather than by
 * service code, so no caller can forget to set them or backdate them. There are
 * deliberately no setters: these columns are infrastructure, not domain state.
 *
 * <p>{@link Instant} is used instead of {@code LocalDateTime} so the stored value
 * is an unambiguous point on the timeline. Hibernate is configured to bind these
 * in UTC ({@code hibernate.jdbc.time_zone}), which keeps the data correct even if
 * the application server and the database sit in different zones.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

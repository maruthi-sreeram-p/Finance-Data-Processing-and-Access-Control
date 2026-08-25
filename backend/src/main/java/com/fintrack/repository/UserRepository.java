package com.fintrack.repository;

import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Looks up an account by its login identifier.
     *
     * <p>Emails are normalized to lower case before being persisted, so a plain
     * equality match is correct here and -- unlike {@code IgnoreCase} -- is able to
     * use the unique index on {@code email}.
     */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Used by admin listings to filter by role and/or status. */
    Page<User> findByRole(Role role, Pageable pageable);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    Page<User> findByRoleAndStatus(Role role, UserStatus status, Pageable pageable);

    long countByRole(Role role);
}

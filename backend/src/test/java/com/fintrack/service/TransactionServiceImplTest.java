package com.fintrack.service;

import com.fintrack.dto.transaction.TransactionFilter;
import com.fintrack.dto.transaction.TransactionRequest;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.Role;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.exception.InvalidTransactionException;
import com.fintrack.exception.ResourceNotFoundException;
import com.fintrack.mapper.CategoryMapper;
import com.fintrack.mapper.TransactionMapper;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.security.CurrentUserProvider;
import com.fintrack.security.LedgerAccessPolicy;
import com.fintrack.service.impl.TransactionServiceImpl;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ledger rules in isolation.
 *
 * <p>These complement rather than duplicate the integration tests: the integration
 * suite proves the behaviour end to end, while these pin down <em>which repository
 * method</em> each operation reaches for. That distinction is the whole ownership
 * guarantee -- an update that switched from the scoped lookup to a plain
 * {@code findById} would still pass every green-path test.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private final LedgerAccessPolicy accessPolicy = new LedgerAccessPolicy();
    private final TransactionMapper transactionMapper = new TransactionMapper(new CategoryMapper());

    private TransactionServiceImpl transactionService;

    private User alice;
    private Category rent;
    private Category salary;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(
                transactionRepository, categoryRepository, currentUserProvider, accessPolicy, transactionMapper);

        alice = TestDataFactory.user("alice@fintrack.test");
        alice.setId(1L);

        rent = TestDataFactory.category("Rent", CategoryType.EXPENSE);
        rent.setId(10L);

        salary = TestDataFactory.category("Salary", CategoryType.INCOME);
        salary.setId(11L);
    }

    @Test
    @DisplayName("reads through the ownership-scoped query for an ordinary caller")
    void ordinaryCallerUsesScopedLookup() {
        Transaction owned = transaction(alice, rent, 100L);
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(transactionRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(owned));

        transactionService.getById(100L);

        // The owner is part of the WHERE clause, so a foreign id simply returns
        // nothing. The unscoped lookup must never be reached on this path.
        verify(transactionRepository).findByIdAndUserId(100L, 1L);
        verify(transactionRepository, never()).findWithDetailsById(any());
    }

    @Test
    @DisplayName("reads through the unscoped query only for a caller with cross-user privilege")
    void privilegedCallerUsesUnscopedLookup() {
        User manager = TestDataFactory.user("manager@fintrack.test");
        manager.setId(2L);
        manager.setRole(Role.MANAGER);

        Transaction someoneElses = transaction(alice, rent, 100L);
        when(currentUserProvider.requireCurrentUser()).thenReturn(manager);
        when(transactionRepository.findWithDetailsById(100L)).thenReturn(Optional.of(someoneElses));

        transactionService.getById(100L);

        verify(transactionRepository).findWithDetailsById(100L);
        verify(transactionRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("updates through the ownership-scoped query regardless of role")
    void updateAlwaysUsesScopedLookup() {
        when(currentUserProvider.requireCurrentUserId()).thenReturn(1L);
        when(transactionRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(100L, request(TransactionType.EXPENSE, rent.getId())))
                .isInstanceOf(ResourceNotFoundException.class);

        // Write access never widens with privilege, so there is no branch here at all.
        verify(transactionRepository, never()).findWithDetailsById(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletes through the ownership-scoped query")
    void deleteUsesScopedLookup() {
        when(currentUserProvider.requireCurrentUserId()).thenReturn(1L);
        when(transactionRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(100L))
                .isInstanceOf(ResourceNotFoundException.class);

        // Typed matcher: TransactionRepository inherits delete(T) from CrudRepository
        // and delete(Specification) from JpaSpecificationExecutor, so a bare any() is
        // ambiguous.
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    @DisplayName("refuses an expense filed under an income-only category")
    void refusesIncompatibleCategory() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(salary));

        assertThatThrownBy(() ->
                transactionService.create(request(TransactionType.EXPENSE, salary.getId())))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Salary");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("accepts a BOTH category for either direction")
    void acceptsBothCategory() {
        Category investment = TestDataFactory.category("Investment", CategoryType.BOTH);
        investment.setId(12L);

        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(investment));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(call -> {
            Transaction saved = call.getArgument(0);
            saved.setId(200L);
            return saved;
        });

        transactionService.create(request(TransactionType.INCOME, investment.getId()));

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("stamps the caller as owner, ignoring anything the request could have carried")
    void ownerComesFromSecurityContext() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(rent));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(call -> {
            Transaction saved = call.getArgument(0);
            saved.setId(200L);
            return saved;
        });

        transactionService.create(request(TransactionType.EXPENSE, rent.getId()));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(alice);
    }

    @Test
    @DisplayName("reports an unknown category as not found")
    void reportsUnknownCategory() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(request(TransactionType.EXPENSE, 999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    @DisplayName("rejects an inverted date range before touching the database")
    void rejectsInvertedDateRange() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);

        TransactionFilter inverted = new TransactionFilter(
                null, null, null, null,
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1), null, null);

        assertThatThrownBy(() -> transactionService.list(inverted, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidTransactionException.class);

        verify(transactionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("rejects an inverted amount range")
    void rejectsInvertedAmountRange() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);

        TransactionFilter inverted = new TransactionFilter(
                null, null, null, null, null, null,
                new BigDecimal("500.00"), new BigDecimal("100.00"));

        assertThatThrownBy(() -> transactionService.list(inverted, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    @DisplayName("sanitizes the sort before querying")
    void sanitizesSortBeforeQuerying() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        transactionService.list(emptyFilter(), PageRequest.of(0, 20, Sort.by("user.passwordHash")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), captor.capture());

        Sort effectiveSort = captor.getValue().getSort();
        assertThat(effectiveSort.getOrderFor("user.passwordHash")).isNull();
        assertThat(effectiveSort.getOrderFor("id")).isNotNull();
    }

    @Test
    @DisplayName("looks up a category filter given by name")
    void resolvesCategoryFilterByName() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(categoryRepository.findByNameIgnoreCase("Rent")).thenReturn(Optional.of(rent));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        TransactionFilter byName = new TransactionFilter(
                null, null, null, "Rent", null, null, null, null);

        transactionService.list(byName, PageRequest.of(0, 20));

        verify(categoryRepository).findByNameIgnoreCase("Rent");
    }

    @Test
    @DisplayName("does not look up a name when an explicit category id was given")
    void categoryIdWinsOverName() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(alice);
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        TransactionFilter both = new TransactionFilter(
                null, null, 10L, "Food", null, null, null, null);

        transactionService.list(both, PageRequest.of(0, 20));

        verify(categoryRepository, never()).findByNameIgnoreCase(eq("Food"));
    }

    // --- Helpers -------------------------------------------------------------

    private static TransactionFilter emptyFilter() {
        return new TransactionFilter(null, null, null, null, null, null, null, null);
    }

    private static Page<Transaction> emptyPage() {
        return new PageImpl<>(List.of());
    }

    private static TransactionRequest request(TransactionType type, Long categoryId) {
        return new TransactionRequest(
                type, new BigDecimal("100.00"), categoryId, "Test", LocalDate.of(2026, 8, 1));
    }

    private static Transaction transaction(User owner, Category category, Long id) {
        Transaction transaction = TestDataFactory.expense(owner, category, "100.00");
        transaction.setId(id);
        return transaction;
    }
}

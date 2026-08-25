package com.fintrack.service.impl;

import com.fintrack.dto.common.PagedResponse;
import com.fintrack.dto.transaction.TransactionFilter;
import com.fintrack.dto.transaction.TransactionRequest;
import com.fintrack.dto.transaction.TransactionResponse;
import com.fintrack.entity.Category;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.User;
import com.fintrack.exception.InvalidTransactionException;
import com.fintrack.exception.ResourceNotFoundException;
import com.fintrack.mapper.TransactionMapper;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.TransactionSpecifications;
import com.fintrack.security.CurrentUserProvider;
import com.fintrack.security.LedgerAccessPolicy;
import com.fintrack.service.TransactionService;
import com.fintrack.util.SortSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final String RESOURCE = "Transaction";

    /** No category can have this id, so a filter using it matches nothing. */
    private static final Long NO_SUCH_CATEGORY = -1L;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final LedgerAccessPolicy accessPolicy;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        User owner = currentUserProvider.requireCurrentUser();
        Category category = loadCompatibleCategory(request);

        Transaction transaction = transactionMapper.toEntity(request, owner, category);
        Transaction saved = transactionRepository.save(transaction);

        // The amount is not logged: it is the substance of the record, and an
        // application log is the wrong place for a user's financial detail.
        log.info("User {} created transaction {}", owner.getId(), saved.getId());
        return transactionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getById(Long id) {
        User caller = currentUserProvider.requireCurrentUser();

        Transaction transaction = accessPolicy.canReadAnyLedger(caller.getRole())
                ? transactionRepository.findWithDetailsById(id)
                        .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id))
                // For an ordinary caller the owner is part of the query, so a record
                // belonging to somebody else comes back empty and is reported exactly
                // like one that does not exist.
                : transactionRepository.findByIdAndUserId(id, caller.getId())
                        .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));

        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> list(TransactionFilter filter, Pageable pageable) {
        User caller = currentUserProvider.requireCurrentUser();
        rejectInvertedRanges(filter);

        // The policy, not the query string, decides whose ledger is read. A caller
        // without cross-user privilege is pinned to their own id whatever they asked for.
        Long ledgerOwnerId = accessPolicy.resolveLedgerOwner(
                caller.getId(), caller.getRole(), filter.userId());

        Specification<Transaction> specification = buildSpecification(filter, ledgerOwnerId);
        Pageable safePageable = SortSanitizer.sanitizeTransactionSort(pageable);

        Page<Transaction> page = transactionRepository.findAll(specification, safePageable);
        return PagedResponse.from(page, transactionMapper::toResponse);
    }

    @Override
    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Long callerId = currentUserProvider.requireCurrentUserId();

        // Ownership-scoped from the start. Even an administrator cannot reach
        // another user's record here -- write access never widens with privilege.
        Transaction transaction = transactionRepository.findByIdAndUserId(id, callerId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));

        Category category = loadCompatibleCategory(request);
        transactionMapper.applyUpdate(transaction, request, category);

        Transaction saved = transactionRepository.save(transaction);
        log.info("User {} updated transaction {}", callerId, saved.getId());
        return transactionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long callerId = currentUserProvider.requireCurrentUserId();

        Transaction transaction = transactionRepository.findByIdAndUserId(id, callerId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));

        transactionRepository.delete(transaction);
        log.info("User {} deleted transaction {}", callerId, id);
    }

    // --- Helpers -------------------------------------------------------------

    private Specification<Transaction> buildSpecification(TransactionFilter filter, Long ledgerOwnerId) {
        Long categoryId = resolveCategoryId(filter);

        // Every optional predicate returns null when its argument is absent and is
        // dropped by Spring Data, so the chain needs no branching.
        return Specification.allOf(
                TransactionSpecifications.ownedBy(ledgerOwnerId),
                TransactionSpecifications.hasType(filter.type()),
                TransactionSpecifications.hasCategoryId(categoryId),
                TransactionSpecifications.dateFrom(filter.from()),
                TransactionSpecifications.dateTo(filter.to()),
                TransactionSpecifications.amountAtLeast(filter.minAmount()),
                TransactionSpecifications.amountAtMost(filter.maxAmount()));
    }

    /**
     * Resolves the category filter, which may arrive as an id or as a name.
     *
     * <p>An unknown name yields a sentinel that matches nothing, rather than being
     * ignored. Silently dropping the filter would answer {@code ?category=Foodd}
     * with the user's entire ledger, which looks like a working query returning
     * wrong results -- the worst possible outcome.
     */
    private Long resolveCategoryId(TransactionFilter filter) {
        if (filter.categoryId() != null) {
            return filter.categoryId();
        }
        if (!filter.filtersByCategoryName()) {
            return null;
        }
        return categoryRepository.findByNameIgnoreCase(filter.category().trim())
                .map(Category::getId)
                .orElse(NO_SUCH_CATEGORY);
    }

    /**
     * Loads the requested category and checks it admits this direction of transaction.
     *
     * <p>This is the rule Bean Validation cannot express: it spans the request and
     * the categories table. Without it, a salary could be filed under "Rent" and
     * every dashboard aggregate built on category would quietly be wrong.
     */
    private Category loadCompatibleCategory(TransactionRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));

        if (!category.getType().accepts(request.type())) {
            throw new InvalidTransactionException(
                    "Category '%s' cannot be used for %s transactions"
                            .formatted(category.getName(), request.type()));
        }
        return category;
    }

    private void rejectInvertedRanges(TransactionFilter filter) {
        if (filter.hasInvertedDateRange()) {
            throw new InvalidTransactionException("The 'from' date must not be after the 'to' date");
        }
        if (filter.hasInvertedAmountRange()) {
            throw new InvalidTransactionException("minAmount must not be greater than maxAmount");
        }
    }
}

package com.fintrack.mapper;

import com.fintrack.dto.transaction.TransactionRequest;
import com.fintrack.dto.transaction.TransactionResponse;
import com.fintrack.entity.Category;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Converts between {@link Transaction} entities and their API representations.
 *
 * <p>Note that {@link #toEntity} takes the owner and the category as arguments
 * rather than reading them from the request. The request carries neither: the owner
 * comes from the security context and the category has already been loaded and
 * validated by the service. Keeping the mapper unable to resolve either means it
 * cannot accidentally become the place where an unvalidated owner slips in.
 */
@Component
@RequiredArgsConstructor
public class TransactionMapper {

    private final CategoryMapper categoryMapper;

    /**
     * Touches {@code getUser().getId()} and the category, both lazy associations.
     * Callers must supply entities loaded with those graphs -- the repository
     * methods used by the service do exactly that -- or this runs inside an open
     * transaction.
     */
    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getUser().getId(),
                transaction.getType(),
                transaction.getAmount(),
                categoryMapper.toResponse(transaction.getCategory()),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }

    public Transaction toEntity(TransactionRequest request, User owner, Category category) {
        return Transaction.builder()
                .user(owner)
                .category(category)
                .type(request.type())
                .amount(request.amount())
                .description(trimToNull(request.description()))
                .transactionDate(request.transactionDate())
                .build();
    }

    /**
     * Applies an update in place.
     *
     * <p>The owner is deliberately not among the fields written. An update must
     * never be able to move a record into a different user's ledger, and the way to
     * guarantee that is for no code path to exist that could.
     */
    public void applyUpdate(Transaction transaction, TransactionRequest request, Category category) {
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setCategory(category);
        transaction.setDescription(trimToNull(request.description()));
        transaction.setTransactionDate(request.transactionDate());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

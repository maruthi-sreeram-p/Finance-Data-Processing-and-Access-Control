package com.fintrack.service;

import com.fintrack.dto.common.PagedResponse;
import com.fintrack.dto.transaction.TransactionFilter;
import com.fintrack.dto.transaction.TransactionRequest;
import com.fintrack.dto.transaction.TransactionResponse;
import org.springframework.data.domain.Pageable;

/**
 * The transaction ledger.
 *
 * <p>No method takes an owner argument. Every operation resolves the caller from
 * the security context and applies {@code LedgerAccessPolicy} itself, so there is
 * no signature through which a controller could pass the wrong owner -- by mistake
 * or because a request parameter said so.
 */
public interface TransactionService {

    /** Creates a transaction owned by the caller. */
    TransactionResponse create(TransactionRequest request);

    /**
     * Returns one transaction.
     *
     * @throws com.fintrack.exception.ResourceNotFoundException if it does not exist,
     *         or the caller is not entitled to see it. The two are indistinguishable
     *         by design, so the endpoint cannot be used to discover which ids exist.
     */
    TransactionResponse getById(Long id);

    /**
     * Returns a filtered, sorted page of transactions from exactly one ledger --
     * the caller's own, unless they hold cross-user read privilege and asked for
     * another.
     */
    PagedResponse<TransactionResponse> list(TransactionFilter filter, Pageable pageable);

    /**
     * Replaces a transaction the caller owns.
     *
     * @throws com.fintrack.exception.ResourceNotFoundException if it does not exist
     *         or is not the caller's
     */
    TransactionResponse update(Long id, TransactionRequest request);

    /**
     * Deletes a transaction the caller owns.
     *
     * @throws com.fintrack.exception.ResourceNotFoundException if it does not exist
     *         or is not the caller's
     */
    void delete(Long id);
}

package com.fintrack.service;

import com.fintrack.dto.category.CategoryRequest;
import com.fintrack.dto.category.CategoryResponse;
import com.fintrack.entity.TransactionType;

import java.util.List;

/**
 * Reference categories.
 *
 * <p>Reading is unpaginated on purpose: this is a small, bounded lookup table that
 * every client needs in full to render a picker, and paging it would only add a
 * round trip.
 */
public interface CategoryService {

    List<CategoryResponse> findAll();

    /**
     * Categories a transaction of the given direction may use -- the matching type
     * plus every BOTH category. Lets a client offer only valid choices instead of
     * discovering the rule from a rejected request.
     */
    List<CategoryResponse> findUsableBy(TransactionType transactionType);

    CategoryResponse getById(Long id);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    /**
     * Deletes a category.
     *
     * @throws com.fintrack.exception.ForbiddenException if any transaction still
     *         references it; removing it would orphan financial records
     */
    void delete(Long id);
}

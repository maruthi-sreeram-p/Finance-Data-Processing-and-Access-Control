package com.fintrack.service.impl;

import com.fintrack.dto.category.CategoryRequest;
import com.fintrack.dto.category.CategoryResponse;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.TransactionType;
import com.fintrack.exception.DuplicateResourceException;
import com.fintrack.exception.ForbiddenException;
import com.fintrack.exception.ResourceNotFoundException;
import com.fintrack.mapper.CategoryMapper;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String RESOURCE = "Category";

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findUsableBy(TransactionType transactionType) {
        if (transactionType == null) {
            return findAll();
        }

        // The matching type plus BOTH, which is what CategoryType.accepts encodes.
        List<CategoryType> usable = transactionType == TransactionType.INCOME
                ? List.of(CategoryType.INCOME, CategoryType.BOTH)
                : List.of(CategoryType.EXPENSE, CategoryType.BOTH);

        return categoryRepository.findByTypeInOrderByNameAsc(usable).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return categoryMapper.toResponse(loadCategory(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw DuplicateResourceException.of(RESOURCE, "name", name);
        }

        Category saved = categoryRepository.save(categoryMapper.toEntity(request));
        log.info("Created category {} ({})", saved.getId(), saved.getName());
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = loadCategory(id);
        String name = request.name().trim();

        // Compared case-insensitively against the existing name so that merely
        // changing the capitalization is not mistaken for a clash with itself.
        if (!name.equalsIgnoreCase(category.getName()) && categoryRepository.existsByNameIgnoreCase(name)) {
            throw DuplicateResourceException.of(RESOURCE, "name", name);
        }

        categoryMapper.applyUpdate(category, request);
        Category saved = categoryRepository.save(category);

        log.info("Updated category {} ({})", saved.getId(), saved.getName());
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = loadCategory(id);

        // Checked before deleting so the caller gets an explanation rather than a
        // foreign-key violation surfacing as a generic conflict. The constraint
        // itself remains the real guarantee.
        if (transactionRepository.existsByCategoryId(id)) {
            throw new ForbiddenException(
                    "Category '%s' is still used by existing transactions and cannot be deleted"
                            .formatted(category.getName()));
        }

        categoryRepository.delete(category);
        log.info("Deleted category {} ({})", id, category.getName());
    }

    private Category loadCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }
}

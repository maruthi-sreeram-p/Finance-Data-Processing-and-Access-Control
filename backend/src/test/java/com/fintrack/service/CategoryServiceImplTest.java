package com.fintrack.service;

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
import com.fintrack.service.impl.CategoryServiceImpl;
import com.fintrack.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private final CategoryMapper categoryMapper = new CategoryMapper();

    private CategoryServiceImpl categoryService;

    private Category rent;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, transactionRepository, categoryMapper);

        rent = TestDataFactory.category("Rent", CategoryType.EXPENSE);
        rent.setId(10L);
    }

    @Test
    @DisplayName("offers the matching type plus every BOTH category for a direction")
    void findsCategoriesUsableByDirection() {
        when(categoryRepository.findByTypeInOrderByNameAsc(anyList())).thenReturn(List.of(rent));

        categoryService.findUsableBy(TransactionType.EXPENSE);

        // A client can then present only valid choices instead of learning the rule
        // from a rejected request.
        verify(categoryRepository).findByTypeInOrderByNameAsc(
                List.of(CategoryType.EXPENSE, CategoryType.BOTH));
    }

    @Test
    @DisplayName("returns every category when no direction is given")
    void findsAllWhenDirectionOmitted() {
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(rent));

        List<CategoryResponse> categories = categoryService.findUsableBy(null);

        assertThat(categories).hasSize(1);
        verify(categoryRepository, never()).findByTypeInOrderByNameAsc(anyList());
    }

    @Test
    @DisplayName("rejects a name that is already taken, whatever its casing")
    void rejectsDuplicateName() {
        when(categoryRepository.existsByNameIgnoreCase("RENT")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(
                new CategoryRequest("RENT", "Duplicate", CategoryType.EXPENSE)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("allows a category to keep its own name when only its casing changes")
    void allowsRecasingOwnName() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(rent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));

        // Comparing case-insensitively against the current name stops a rename to
        // "RENT" being mistaken for a clash with itself.
        CategoryResponse updated = categoryService.update(10L,
                new CategoryRequest("RENT", "Housing", CategoryType.EXPENSE));

        assertThat(updated.name()).isEqualTo("RENT");
        verify(categoryRepository, never()).existsByNameIgnoreCase(any());
    }

    @Test
    @DisplayName("refuses to delete a category that transactions still reference")
    void refusesDeletingCategoryInUse() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(rent));
        when(transactionRepository.existsByCategoryId(10L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("still used by existing transactions");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deletes a category that nothing references")
    void deletesUnusedCategory() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(rent));
        when(transactionRepository.existsByCategoryId(10L)).thenReturn(false);

        categoryService.delete(10L);

        verify(categoryRepository).delete(rent);
    }

    @Test
    @DisplayName("reports a missing category as not found")
    void reportsMissingCategory() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("stores a blank description as null so absent has one representation")
    void normalizesBlankDescriptionToNull() {
        when(categoryRepository.existsByNameIgnoreCase("Subscriptions")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));

        CategoryResponse created = categoryService.create(
                new CategoryRequest("  Subscriptions  ", "   ", CategoryType.EXPENSE));

        assertThat(created.description()).isNull();
        assertThat(created.name()).isEqualTo("Subscriptions");
    }
}

package com.fintrack.repository;

import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("migration V2 seeds the reference categories")
    void seedsReferenceCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByNameAsc();

        assertThat(categories).hasSize(12);
        assertThat(categories).extracting(Category::getName)
                .containsExactly("Bills", "Education", "Entertainment", "Food", "Freelancing",
                        "Healthcare", "Investment", "Other", "Rent", "Salary", "Shopping", "Transport");
    }

    @Test
    @DisplayName("seeded categories carry the correct income/expense classification")
    void classifiesSeededCategories() {
        assertThat(categoryRepository.findByNameIgnoreCase("Salary"))
                .get().extracting(Category::getType).isEqualTo(CategoryType.INCOME);
        assertThat(categoryRepository.findByNameIgnoreCase("Rent"))
                .get().extracting(Category::getType).isEqualTo(CategoryType.EXPENSE);
        assertThat(categoryRepository.findByNameIgnoreCase("Investment"))
                .get().extracting(Category::getType).isEqualTo(CategoryType.BOTH);
    }

    @Test
    @DisplayName("looks up a category regardless of the casing the client sent")
    void findsCategoryCaseInsensitively() {
        // Supports both ?category=FOOD and ?category=Food from the transaction filter.
        assertThat(categoryRepository.findByNameIgnoreCase("FOOD")).isPresent();
        assertThat(categoryRepository.findByNameIgnoreCase("food")).isPresent();
        assertThat(categoryRepository.findByNameIgnoreCase("NoSuchCategory")).isEmpty();
    }

    @Test
    @DisplayName("returns the categories usable by expense transactions")
    void findsCategoriesUsableByExpenses() {
        List<Category> usable =
                categoryRepository.findByTypeInOrderByNameAsc(List.of(CategoryType.EXPENSE, CategoryType.BOTH));

        assertThat(usable).extracting(Category::getName).contains("Rent", "Food", "Investment", "Other");
        assertThat(usable).extracting(Category::getName).doesNotContain("Salary", "Freelancing");
    }
}

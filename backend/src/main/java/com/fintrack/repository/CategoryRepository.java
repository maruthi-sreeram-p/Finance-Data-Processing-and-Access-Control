package com.fintrack.repository;

import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Category names are user-visible labels rather than normalized keys, so the
     * lookup is case-insensitive. This lets clients filter with
     * {@code ?category=FOOD} as well as {@code ?category=Food}.
     */
    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Category> findAllByOrderByNameAsc();

    /**
     * Categories valid for a given direction, i.e. the matching type plus every
     * {@link CategoryType#BOTH} category.
     */
    List<Category> findByTypeInOrderByNameAsc(List<CategoryType> types);
}

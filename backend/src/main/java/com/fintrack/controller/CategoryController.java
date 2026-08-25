package com.fintrack.controller;

import com.fintrack.dto.category.CategoryRequest;
import com.fintrack.dto.category.CategoryResponse;
import com.fintrack.dto.common.ApiError;
import com.fintrack.entity.Role;
import com.fintrack.entity.TransactionType;
import com.fintrack.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference categories.
 *
 * <p>Readable by anyone signed in -- every client needs the list to render a picker
 * -- but writable only by administrators, because a category is shared by all users
 * and renaming or retyping one is visible to everybody at once.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Shared reference data. Readable by any user, managed by administrators.")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(
            summary = "List categories",
            description = "Returns every category, or -- with ?type= -- only those a transaction of "
                    + "that direction may use, which is the matching type plus every BOTH category. "
                    + "Unpaginated: this is a small bounded lookup table that clients need in full."
    )
    public ResponseEntity<List<CategoryResponse>> list(
            @Parameter(description = "Restrict to categories usable by this transaction direction")
            @RequestParam(required = false) TransactionType type) {

        return ResponseEntity.ok(categoryService.findUsableBy(type));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a category by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The category"),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CategoryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
    @Operation(summary = "Create a category", description = "Administrator only.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "A category with that name already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
    @Operation(summary = "Update a category", description = "Administrator only.")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {

        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
    @Operation(
            summary = "Delete a category",
            description = "Administrator only. Refused with 403 while any transaction still "
                    + "references the category, since deleting it would orphan financial records."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403", description = "Not an administrator, or the category is still in use",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

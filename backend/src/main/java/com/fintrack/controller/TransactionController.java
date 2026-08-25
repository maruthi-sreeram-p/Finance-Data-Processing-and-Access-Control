package com.fintrack.controller;

import com.fintrack.dto.common.ApiError;
import com.fintrack.dto.common.PagedResponse;
import com.fintrack.dto.transaction.TransactionFilter;
import com.fintrack.dto.transaction.TransactionRequest;
import com.fintrack.dto.transaction.TransactionResponse;
import com.fintrack.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * The transaction ledger.
 *
 * <p>Notice that no method here mentions a user. Ownership is not something the
 * HTTP layer can be trusted to establish -- a path variable or a query parameter is
 * whatever the client typed -- so every route resolves the caller from the security
 * context inside {@link TransactionService} instead.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Create, read, filter and manage ledger entries")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(
            summary = "Create a transaction",
            description = "The entry is owned by the authenticated caller. "
                    + "The category must admit the given direction: an EXPENSE cannot be filed "
                    + "under an INCOME-only category such as Salary."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the category "
                    + "does not admit this transaction type",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Category not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = transactionService.create(request);

        // 201 with a Location header pointing at the new resource, as a POST that
        // creates something should.
        URI location = UriComponentsBuilder.fromPath("/api/transactions/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(
            summary = "List transactions",
            description = """
                    Returns one page of the caller's own ledger, filtered and sorted.

                    ADMIN and MANAGER may read another user's ledger with ?userId=. Any other \
                    caller is pinned to their own regardless of what they pass.

                    Sorting accepts transactionDate, amount, type, createdAt, updatedAt and id; \
                    anything else is ignored in favour of the default of transactionDate,desc. \
                    A tiebreaker on id is always appended so paging cannot repeat or skip rows.

                    Example: /api/transactions?type=EXPENSE&category=Food&from=2026-08-01\
                    &to=2026-08-31&page=0&size=20&sort=amount,desc"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of transactions"),
            @ApiResponse(responseCode = "400", description = "An inverted date or amount range",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedResponse<TransactionResponse>> list(
            @ParameterObject @Valid TransactionFilter filter,
            @ParameterObject
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(transactionService.list(filter, pageable));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a transaction",
            description = "Returns the entry if the caller owns it, or holds cross-user read "
                    + "privilege. Otherwise the response is 404, identical to a genuinely missing "
                    + "record, so the endpoint cannot be used to discover which ids exist."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The transaction"),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Not found, or not the caller's to see",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a transaction",
            description = "Only the owner may modify an entry. Cross-user read privilege does not "
                    + "extend to writing, so an administrator editing someone else's record gets 404."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the category "
                    + "does not admit this transaction type",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Not found, or not the caller's",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TransactionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {

        return ResponseEntity.ok(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a transaction",
            description = "Only the owner may delete an entry."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Not found, or not the caller's",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

package com.fintrack.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable envelope for paginated results.
 *
 * <p>Spring's {@code Page} is not returned directly: its JSON shape is an
 * implementation detail that has changed between Spring Data versions, it
 * serializes the whole {@code Pageable} including sort internals, and Boot 3.3+
 * logs a warning for exactly this reason. Declaring the contract here means the
 * API shape is ours to keep stable.
 *
 * @param content       the items on this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of matching items across all pages
 * @param totalPages    total number of pages available
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
@Schema(description = "A single page of results together with its pagination metadata")
public record PagedResponse<T>(
        @Schema(description = "Items on this page")
        List<T> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total matching items across all pages", example = "137")
        long totalElements,

        @Schema(description = "Total number of pages", example = "7")
        int totalPages,

        @Schema(description = "Whether this is the first page", example = "true")
        boolean first,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean last
) {

    /**
     * Wraps a {@link Page} of entities, converting each element with {@code mapper}.
     *
     * <p>The mapping happens here rather than in the caller so that entities can
     * never escape into a response by accident.
     */
    public static <E, D> PagedResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}

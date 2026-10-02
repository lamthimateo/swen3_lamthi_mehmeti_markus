package com.swen3.swen3rest.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * JSON shape for paginated list endpoints.
 *
 * We return our own record instead of serializing Spring's Page directly, so the
 * contract the UI codes against stays stable across Spring versions.
 */
public record PageResponse<T>(
        List<T> content,
        int page,              // zero-based page index
        int size,              // requested page size
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

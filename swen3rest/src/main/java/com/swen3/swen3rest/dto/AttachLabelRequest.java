package com.swen3.swen3rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Input for POST /api/documents/{documentId}/labels, bound from the query parameters
 * {@code labelId} and {@code weight}. A missing weight means the default of 1.0.
 */
public record AttachLabelRequest(
        @NotNull
        Long labelId,

        @PositiveOrZero
        Double weight
) {
}

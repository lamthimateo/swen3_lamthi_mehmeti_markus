package com.swen3.swen3rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input for POST /api/labels, bound from the query parameter {@code name}.
 * The limits match the UI (label names up to 50 characters).
 */
public record CreateLabelRequest(
        @NotBlank
        @Size(max = 50)
        String name
) {
    // trim before validation, so "  " is rejected as blank and " invoice " is stored as "invoice"
    public CreateLabelRequest {
        name = name == null ? null : name.strip();
    }
}

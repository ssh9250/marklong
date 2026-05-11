package com.example.marklong.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostUpdateRequest(
        @NotBlank String title,
        @NotNull String content,
        @NotNull String stockCode
) {
}

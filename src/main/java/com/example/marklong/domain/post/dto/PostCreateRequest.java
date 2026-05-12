package com.example.marklong.domain.post.dto;

import com.example.marklong.domain.post.domain.Post;
import jakarta.validation.constraints.NotBlank;

public record PostCreateRequest(
        @NotBlank
        String stockCode,
        @NotBlank
        String title,
        @NotBlank
        String content
) {
}

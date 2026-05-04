package com.example.marklong.domain.post.dto;

public record PostUpdateRequest(
        String title,
        String content,
        String stockCode
) {
}

package com.example.marklong.domain.post.dto;

import com.example.marklong.domain.post.domain.Post;

import java.time.LocalDateTime;

public record PostListResponse(
        Long id,
        String stockCode,
        String title,
        String writer,
        int likeCount,
        LocalDateTime createdAt
) {
    public static PostListResponse from(Post post, String writer) {
        return new PostListResponse(post.getId(), post.getStockCode(), post.getTitle(), writer, post.getLikeCount(), post.getCreatedAt());
    }
}

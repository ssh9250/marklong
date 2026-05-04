package com.example.marklong.domain.post.dto;

public record CommentCreateRequest(
        Long postId,
        String content
) {
}

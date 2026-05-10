package com.example.marklong.domain.post.dto;


import com.example.marklong.domain.post.domain.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        String writer,
        int likeCount,
        LocalDateTime createdAt
) {
    public static CommentResponse from(Comment comment, String writer) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                writer,
                comment.getLikeCount(),
                comment.getCreatedAt()
        );
    }
}

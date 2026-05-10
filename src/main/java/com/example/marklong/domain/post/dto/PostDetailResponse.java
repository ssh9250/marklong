package com.example.marklong.domain.post.dto;

import com.example.marklong.domain.post.domain.Comment;
import com.example.marklong.domain.post.domain.Post;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long id,
        String stockCode,
        String title,
        String content,
        String writer,
        int likeCount,
        List<CommentResponse> comments,
        LocalDateTime createdAt
) {
    public static PostDetailResponse from(Post post, String writer, List<CommentResponse> comments) {
        return new PostDetailResponse(
                post.getId(),
                post.getStockCode(),
                post.getTitle(),
                post.getContent(),
                writer,
                post.getLikeCount(),
                comments,
                post.getCreatedAt()
        );
    }
    // get post -> post 1, find comments by postId -> comment list
}

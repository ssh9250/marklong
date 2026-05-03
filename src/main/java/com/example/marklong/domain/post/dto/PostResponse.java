package com.example.marklong.domain.post.dto;

import com.example.marklong.domain.post.domain.Post;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostResponse {
    private String stockCode;
    private String title;
    private String content;

    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .stockCode(post.getStockCode())
                .title(post.getTitle())
                .content(post.getContent())
                .build();
    }
}

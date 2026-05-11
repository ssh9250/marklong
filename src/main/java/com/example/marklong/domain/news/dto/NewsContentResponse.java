package com.example.marklong.domain.news.dto;

import com.example.marklong.domain.news.domain.*;

import java.time.LocalDateTime;

public record NewsContentResponse(
        Long id,
        Long newsId,
        String content
) {
    public static NewsContentResponse from(NewsContent newsContent) {
        return new NewsContentResponse(
                newsContent.getId(),
                newsContent.getNewsId(),
                newsContent.getContent()
        );
    }
}


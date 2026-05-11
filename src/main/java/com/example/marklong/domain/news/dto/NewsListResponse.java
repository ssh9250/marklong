package com.example.marklong.domain.news.dto;

import com.example.marklong.domain.news.domain.Category;
import com.example.marklong.domain.news.domain.Importance;
import com.example.marklong.domain.news.domain.Sentiment;

import java.time.LocalDateTime;

public record NewsListResponse(
        Long id,
        String summary,
        String stockCode,
        Category category,
        Sentiment sentiment,
        Importance importance,
        LocalDateTime publishedAt
) {
}

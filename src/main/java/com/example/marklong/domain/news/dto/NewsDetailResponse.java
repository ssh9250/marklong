package com.example.marklong.domain.news.dto;

import com.example.marklong.domain.news.domain.Category;
import com.example.marklong.domain.news.domain.Importance;
import com.example.marklong.domain.news.domain.News;
import com.example.marklong.domain.news.domain.Sentiment;

import java.time.LocalDateTime;

public record NewsDetailResponse(
        Long id,
        String sourceId,
        String provider,
        String author,
        String summary,
        String originalUrl,
        String stockCode,
        Category category,
        Sentiment sentiment,
        Importance importance,
        LocalDateTime publishedAt
) {
    public static NewsDetailResponse from(News news) {
        return new NewsDetailResponse(
                news.getId(),
                news.getSourceId(),
                news.getProvider(),
                news.getAuthor(),
                news.getSummary(),
                news.getOriginalUrl(),
                news.getStockCode(),
                news.getCategory(),
                news.getSentiment(),
                news.getImportance(),
                news.getPublishedAt()
        );
    }
}

package com.example.marklong.domain.news.dto;

import com.example.marklong.domain.news.domain.Category;
import com.example.marklong.domain.news.domain.Importance;
import com.example.marklong.domain.news.domain.Sentiment;

import java.time.LocalDateTime;
import java.util.Set;

public record NewsSearchCondition(
        String provider,
        String author,
        String title,
        String summary,
        String stockCode,
        Set<Category> category,
        Set<Sentiment> sentiment,
        Set<Importance> importance,
        LocalDateTime from
) {
}

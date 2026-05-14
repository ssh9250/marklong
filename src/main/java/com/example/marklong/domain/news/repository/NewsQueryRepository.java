package com.example.marklong.domain.news.repository;

import com.example.marklong.domain.news.domain.Category;
import com.example.marklong.domain.news.domain.Importance;
import com.example.marklong.domain.news.domain.Sentiment;
import com.example.marklong.domain.news.dto.NewsListResponse;
import com.example.marklong.domain.news.dto.NewsSearchCondition;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.example.marklong.domain.news.domain.QNews.news;

@Repository
@RequiredArgsConstructor
public class NewsQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<NewsListResponse> get() {
        return queryFactory
                .select(Projections.constructor(NewsListResponse.class,
                        news.id, news.summary, news.stockCode, news.category, news.sentiment, news.importance, news.publishedAt))
                .from(news)
                .orderBy(news.publishedAt.desc())
                .fetch();
    }

    // slice return type 고려
    public List<NewsListResponse> searchNews(NewsSearchCondition condition) {
        return queryFactory
                .select(Projections.constructor(NewsListResponse.class,
                        news.id, news.summary, news.stockCode, news.category, news.sentiment, news.importance, news.publishedAt))
                .from(news)
                .where(

                )
                .orderBy(news.publishedAt.desc())
                .fetch();
    }

    private BooleanExpression providerContains(String provider) {
        return provider != null ? news.provider.contains(provider) : null;
    }

    private BooleanExpression authorContains(String author) {
        return author != null ? news.author.contains(author) : null;
    }

    private BooleanExpression titleContains(String title) {
        return title != null ? news.title.contains(title) : null;
    }

    private BooleanExpression summaryContains(String summary) {
        return summary != null ? news.summary.contains(summary) : null;
    }

    private BooleanExpression stockCodeEq(String stockCode) {
        return stockCode != null ? news.stockCode.eq(stockCode) : null;
    }

    private BooleanExpression categoryIn(Set<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return news.category.in(categories);
    }

    private BooleanExpression sentimentIn(Set<Sentiment> sentiments) {
        if (sentiments == null || sentiments.isEmpty()) {
            return null;
        }
        return news.sentiment.in(sentiments);
    }

    private BooleanExpression importanceIn(Set<Importance> importances) {
        if (importances == null || importances.isEmpty()) {
            return null;
        }
        return news.importance.in(importances);
    }

    private BooleanExpression publishedFrom(LocalDateTime from) {
        return from != null ? news.publishedAt.goe(from) : null;
    }
}

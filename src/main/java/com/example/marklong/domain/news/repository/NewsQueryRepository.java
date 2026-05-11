package com.example.marklong.domain.news.repository;

import com.example.marklong.domain.news.dto.NewsListResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}

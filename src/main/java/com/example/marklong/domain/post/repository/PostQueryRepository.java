package com.example.marklong.domain.post.repository;

import com.example.marklong.domain.post.dto.PostResponse;
import com.example.marklong.domain.post.dto.PostSearchCondition;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.marklong.domain.post.domain.QPost.post;
import static org.springframework.util.StringUtils.hasText;

@Repository
@RequiredArgsConstructor
public class PostQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<PostResponse> searchPosts(PostSearchCondition condition) {
        return queryFactory.select(Projections.constructor(PostResponse.class,
                        post.stockCode, post.title, post.content))
                .from(post)
                .where()
                .fetch();
    }

    private BooleanExpression stockCodeEq(String stockCode) {
        return hasText(stockCode) ? post.stockCode.eq(stockCode) : null;
    }

    private BooleanExpression titleContains(String cond) {
        return cond != null ? post.title.contains(cond) : null;
    }

    private BooleanExpression contentContains(String cond) {
        return cond != null ? post.content.contains(cond) : null;
    }



}

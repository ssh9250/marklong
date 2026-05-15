package com.example.marklong.domain.post.repository;

import com.example.marklong.domain.post.domain.PostSortType;
import com.example.marklong.domain.post.dto.PostListResponse;
import com.example.marklong.domain.post.dto.PostSearchCondition;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.marklong.domain.user.domain.QUser.user;
import static com.example.marklong.domain.post.domain.QPost.post;
import static org.springframework.util.StringUtils.hasText;

@Repository
@RequiredArgsConstructor
public class PostQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<PostListResponse> searchPosts(PostSearchCondition condition) {
        return queryFactory.select(Projections.constructor(PostListResponse.class,
                        post.stockCode, post.title, user.nickname, post.likeCount, post.createdAt))
                .from(post)
                .join(user).on(post.userId.eq(user.id))
                .where(
                        user.deleted.isNull(),
                        post.deleted.isNull(),

                        stockCodeEq(condition.getStockCode()),
                        titleContains(condition.getTitle()),
                        contentContains(condition.getContent()),
                        writerContains(condition.getWriter()),
                        userIdEq(condition.getUserId()),
                        createdAtFrom(condition.getFrom()),
                        createdAtTo(condition.getTo())
                )
                .orderBy(toOrderSpecifier(condition.getSortType()))
                .fetch();
    }

    private OrderSpecifier<?> toOrderSpecifier(PostSortType sortType) {
        if (sortType == null) {
            return post.createdAt.desc();
        }
        return switch (sortType) {
            case LATEST -> post.createdAt.desc();
            case POPULAR -> post.likeCount.desc();
        };
    }

    // contains() = LIKE '%keyword%' - 인덱스 미사용
    // 성능 튜닝 필요 (postgre sql tsvector, elastic search)

    private BooleanExpression stockCodeEq(String stockCode) {
        return hasText(stockCode) ? post.stockCode.eq(stockCode) : null;
    }

    private BooleanExpression titleContains(String cond) {
        return hasText(cond) ? post.title.contains(cond) : null;
    }

    private BooleanExpression contentContains(String cond) {
        return hasText(cond) ? post.content.contains(cond) : null;
    }

    private BooleanExpression writerContains(String cond) {
        return hasText(cond) ? user.nickname.contains(cond) : null;
    }

    private BooleanExpression userIdEq(Long userId) {
        return userId != null ? post.userId.eq(userId) : null;
    }

    private BooleanExpression createdAtFrom(LocalDateTime from) {
        return from != null ? post.createdAt.goe(from) : null;
    }

    private BooleanExpression createdAtTo(LocalDateTime to) {
        return to != null ? post.createdAt.lt(to.plusDays(1)) : null;
    }
}

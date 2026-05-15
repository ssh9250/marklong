package com.example.marklong.domain.post.repository;

import com.example.marklong.domain.post.dto.CommentResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.marklong.domain.user.domain.QUser.user;
import static com.example.marklong.domain.post.domain.QComment.comment;

@Repository
@RequiredArgsConstructor
public class CommentQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<CommentResponse> getCommentsByPostId(Long postId) {
        return query(comment.postId.eq(postId));
    }

    public List<CommentResponse> getCommentsByUserId(Long userId) {
        return query(comment.userId.eq(userId));
    }

    public List<CommentResponse> query(BooleanExpression condition) {
        return queryFactory.select(Projections.constructor(CommentResponse.class,
                        comment.content, user.nickname, comment.createdAt
                ))
                .from(comment)
                .join(user).on(comment.userId.eq(user.id))
                .where(
                        comment.deletedAt.isNull(),
                        user.deletedAt.isNull(),
                        condition
                )
                .orderBy(comment.createdAt.asc())
                .fetch();
    }
}

package com.example.marklong.domain.post.repository;

import com.example.marklong.domain.post.dto.CommentResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.marklong.domain.auth.domain.QUser.user;
import static com.example.marklong.domain.post.domain.QComment.comment;

@Repository
@RequiredArgsConstructor
public class CommentQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<CommentResponse> getCommentsByPostId(Long postId) {
        return queryFactory.select(Projections.constructor(CommentResponse.class,
                        comment.content, user.nickname, comment.createdAt
                ))
                .from(comment)
                .join(user).on(user.id.eq(comment.userId))
                .where(
                        comment.postId.eq(postId),
                        comment.deletedAt.isNull()
                )
                .orderBy(comment.createdAt.asc())
                .fetch();
    }
}

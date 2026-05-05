package com.example.marklong.domain.post.service;

import com.example.marklong.domain.auth.repository.UserRepository;
import com.example.marklong.domain.post.domain.Comment;
import com.example.marklong.domain.post.dto.CommentCreateRequest;
import com.example.marklong.domain.post.dto.CommentResponse;
import com.example.marklong.domain.post.dto.CommentUpdateRequest;
import com.example.marklong.domain.post.repository.CommentQueryRepository;
import com.example.marklong.domain.post.repository.CommentRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.example.marklong.global.util.OwnerValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final CommentQueryRepository commentQueryRepository;
    private final UserRepository userRepository;

    public Long create(Long userId, Long postId, CommentCreateRequest request) {
        Comment comment = Comment.builder()
                .postId(postId)
                .userId(userId)
                .content(request.content())
                .build();
        commentRepository.save(comment);
        return comment.getId();
    }

    public List<CommentResponse> getCommentsByPostId(Long postId) {
        return commentQueryRepository.getCommentsByPostId(postId);
    }

    public List<CommentResponse> getMyComments(Long userId) {
        return commentQueryRepository.getCommentsByUserId(userId);
    }

    public void update(Long userId, Long commentId, CommentUpdateRequest request) {
        Comment comment = findCommentOrThrow(commentId);
        OwnerValidator.validate(userId, comment.getUserId());
        comment.update(request.content());
    }

    public void delete(Long userId, Long commentId) {
        Comment comment = findCommentOrThrow(commentId);
        OwnerValidator.validate(userId, comment.getUserId());
        comment.delete(); // comment.deletedAt = LocalDateTime.NOW()
    }

    private Comment findCommentOrThrow(Long commentId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private void validateOwner(Long userId, Comment comment) {
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException(ErrorCode.COMMENT_ACCESS_DENIED);
        }
    }
}

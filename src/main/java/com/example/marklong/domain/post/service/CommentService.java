package com.example.marklong.domain.post.service;

import com.example.marklong.domain.post.domain.Comment;
import com.example.marklong.domain.post.dto.CommentCreateRequest;
import com.example.marklong.domain.post.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;

    public Long createComment(Long userId, CommentCreateRequest request) {
        Comment comment = Comment.builder()
                .userId(userId)
                .content(request.content())
                .build();
    }
}

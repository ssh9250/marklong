package com.example.marklong.domain.post.service;

import com.example.marklong.domain.auth.domain.User;
import com.example.marklong.domain.auth.repository.UserRepository;
import com.example.marklong.domain.post.domain.Post;
import com.example.marklong.domain.post.dto.*;
import com.example.marklong.domain.post.repository.CommentQueryRepository;
import com.example.marklong.domain.post.repository.PostQueryRepository;
import com.example.marklong.domain.post.repository.PostRepository;
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
public class PostService {
    private final PostRepository postRepository;
    private final PostQueryRepository postQueryRepository;
    private final UserRepository userRepository;
    private final CommentQueryRepository commentQueryRepository;

    public Long create(Long userId, PostCreateRequest request) {
        Post post = Post.builder()
                .stockCode(request.stockCode())
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .build();


        return postRepository.save(post).getId();
    }

    // post list
    // search cond : stock code, date range, order type,
    public List<PostListResponse> searchPosts(PostSearchCondition condition) {
        return postQueryRepository.searchPosts(condition);
    }

    public List<PostListResponse> getMyPosts(Long userId, PostSearchCondition condition) {
        PostSearchCondition myCondition = condition.toBuilder()
                .userId(userId)
                .writer(null)
                .build();
        return postQueryRepository.searchPosts(myCondition);
    }

    public PostDetailResponse getPost(Long postId) {
        Post post = getPostOrThrow(postId);

        User user = userRepository.findUserByIdAndDeletedAtIsNull(post.getUserId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String writer = user.getNickname();


        return PostDetailResponse.from(post, writer, commentQueryRepository.getCommentsByPostId(postId));
    }

    public void update(Long userId, Long postId, PostUpdateRequest request) {
        Post post = getPostOrThrow(postId);
        OwnerValidator.validate(userId, post.getUserId());
        post.update(request.title(), request.content());
    }

    public void delete(Long userId, Long postId) {
        Post post = getPostOrThrow(postId);
        OwnerValidator.validate(userId, post.getUserId());
        post.delete();
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private void validateOwner(Long userId, Post post) {
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
    }
}

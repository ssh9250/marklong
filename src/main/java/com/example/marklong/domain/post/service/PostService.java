package com.example.marklong.domain.post.service;

import com.example.marklong.domain.post.domain.Post;
import com.example.marklong.domain.post.dto.PostCreateRequest;
import com.example.marklong.domain.post.dto.PostResponse;
import com.example.marklong.domain.post.dto.PostSearchCondition;
import com.example.marklong.domain.post.repository.PostQueryRepository;
import com.example.marklong.domain.post.repository.PostRepository;
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

    public PostResponse create(Long userId, PostCreateRequest request) {
        Post post = Post.builder()
                .stockCode(request.stockCode())
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .build();

        postRepository.save(post);
        return PostResponse.from(post);
    }

    // search cond : stock code, date range, order type,
    public List<PostResponse> searchPosts(PostSearchCondition condition) {
        return postQueryRepository.searchPosts(condition);
    }

    public PostResponse getPost(Long postId) {
        return PostResponse.from(postRepository.findById(postId).orElse(null));
    }
}

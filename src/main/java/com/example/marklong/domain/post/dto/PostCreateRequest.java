package com.example.marklong.domain.post.dto;

import com.example.marklong.domain.post.domain.Post;

public record PostCreateRequest(String stockCode, String title, String content) {
}

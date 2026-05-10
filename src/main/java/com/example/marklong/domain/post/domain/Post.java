package com.example.marklong.domain.post.domain;

import com.example.marklong.global.entity.SoftDeleteEntity;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stockCode;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private int likeCount;

    @Builder
    public Post(String stockCode, Long userId, String title, String content) {
        this.stockCode = stockCode;
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.likeCount = 0;
    }

    public void update(String title, String content) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }
}

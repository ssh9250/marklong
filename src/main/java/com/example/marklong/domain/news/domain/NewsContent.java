package com.example.marklong.domain.news.domain;

import com.example.marklong.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class NewsContent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long newsId;

    private String content;
    // Lob, basic annotation -> lazy loading 방식에서 entity 따로 분리, 필요할 때에만 직접 호출 방식으로 전환

    @Builder
    public NewsContent(Long newsId, String content) {
        this.newsId = newsId;
        this.content = content;
    }
}

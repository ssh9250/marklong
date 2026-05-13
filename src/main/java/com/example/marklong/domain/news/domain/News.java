package com.example.marklong.domain.news.domain;

import com.example.marklong.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class News extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sourceId;    //  크롤러 중복 방지용 외부 원본 id

    private String provider;    //  REUTERS" | "YONHAP" | "DART" 등

    private String author;

    @Column(nullable = false)
    private String title;

    private String summary;

    @Column(nullable = false)
    private String originalUrl;

    private String stockCode;

    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private Sentiment sentiment;

    @Column(nullable = false)
    private Importance importance;

    private LocalDateTime publishedAt;

    @Builder
    private News(
            String sourceId,
            String provider,
            String author,
            String title,
            String summary,
            String originalUrl,
            String stockCode,
            Category category,
            Sentiment sentiment,
            Importance importance,
            LocalDateTime publishedAt
    ) {
        this.sourceId = sourceId;
        this.provider = provider;
        this.author = author;
        this.title = title;
        this.summary = summary;
        this.originalUrl = originalUrl;
        this.stockCode = stockCode;
        this.category = category;
        this.sentiment = sentiment;
        this.importance = importance;
        this.publishedAt = publishedAt;
    }
}

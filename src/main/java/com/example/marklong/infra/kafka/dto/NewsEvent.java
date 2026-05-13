package com.example.marklong.infra.kafka.dto;

import com.example.marklong.domain.news.domain.Category;
import com.example.marklong.domain.news.domain.Importance;
import com.example.marklong.domain.news.domain.Sentiment;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Getter
@Service
@NoArgsConstructor
public class NewsEvent {
    private String sourceId;
    private String provider;      // "naver", "reuters" 등
    private String author;
    private String title;
    private String summary;       // Ollama가 요약한 내용
    private String stockCode;
    private Category category;
    private Sentiment sentiment;     // "POSITIVE" / "NEGATIVE" / "NEUTRAL"
    private Importance importance;
    private String content;       // 본문 (NewsContent로 분리 저장)
    private LocalDateTime publishedAt;
}

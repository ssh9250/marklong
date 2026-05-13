package com.example.marklong.infra.kafka.dto;

import com.example.marklong.domain.news.domain.Importance;
import com.example.marklong.domain.news.domain.Sentiment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertEvent {
    private String stockCode;       // 대상 종목 (전 도메인 공통 키)
    private String newsId;          // 트리거가 된 뉴스 ID
    private String title;           // 알림 제목 ("삼성전자 긍정 뉴스")
    private String summary;         // 한 줄 요약
    private Sentiment sentiment;       // "POSITIVE" / "NEGATIVE" / "NEUTRAL"
    private Importance importance;      // 0.0 ~ 1.0, 임계값 이상만 발행
    private LocalDateTime occurredAt;
}
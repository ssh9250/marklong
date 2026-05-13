package com.example.marklong.infra.kafka;

import com.example.marklong.domain.news.service.NewsService;
import com.example.marklong.infra.kafka.dto.NewsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumer {
    private final NewsService newsService;

    @KafkaListener(
            topics = "news.processed",
            groupId = "marklong-news-group"
    )
    public void consumeNews(NewsEvent newsEvent) {
        if (newsEvent == null) {
            log.warn("역직렬화 실패 - 메시지 스킵");
            return;
        }
        log.info("뉴스 수신: {}", newsEvent.getSourceId());
        try {
            newsService.saveFromKafka(newsEvent);
        } catch (Exception e) {
            log.error("뉴스 저장 실패: {}", newsEvent.getSourceId(), e);
            // todo: DLT(dead letter topic) 연동
        }
    }
}

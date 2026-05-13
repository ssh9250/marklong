package com.example.marklong.infra.kafka;

import com.example.marklong.infra.kafka.dto.StockAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void requestNewsCrawl(String stockCode) {
        kafkaTemplate.send("news.crawl-request", stockCode);
        log.info("크롤 요청: {}", stockCode);
    }

    public void publishStockAlert(StockAlertEvent event) {
        kafkaTemplate.send("stock.alert", event.getStockCode(), event);
    }
}

package com.example.marklong.domain.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class StockService {
    private final SimpMessagingTemplate template;

    public void broadcastRealtimeCandle(String stockCode) {
        template.convertAndSend("/topic/realtimeCandle" + stockCode, "candleData");
    }
}

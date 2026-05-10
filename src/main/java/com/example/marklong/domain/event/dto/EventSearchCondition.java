package com.example.marklong.domain.event.dto;

import com.example.marklong.domain.event.domain.EventSource;
import com.example.marklong.domain.event.domain.EventType;

import java.time.LocalDateTime;
import java.util.Set;

public record EventSearchCondition(
        String stockCode,
        Set<EventType> eventType,
        Set<EventSource> eventSource,
        String title,
        String description,
        LocalDateTime from,
        LocalDateTime to
) {
}

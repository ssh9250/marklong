package com.example.marklong.domain.event.dto;

import com.example.marklong.domain.event.domain.EventSource;
import com.example.marklong.domain.event.domain.EventType;

import java.time.LocalDateTime;

public record EventCreateRequest(
        String stockCode,
        EventType eventType,
        LocalDateTime eventDate,
        String title,
        String description
) {
}

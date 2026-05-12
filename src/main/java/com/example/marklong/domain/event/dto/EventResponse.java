package com.example.marklong.domain.event.dto;

import com.example.marklong.domain.event.domain.CalendarEvent;
import com.example.marklong.domain.event.domain.EventSource;
import com.example.marklong.domain.event.domain.EventType;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String stockCode,
        EventType eventType,
        EventSource eventSource,
        LocalDateTime eventDate,
        String title,
        String description
) {
    public static EventResponse from(CalendarEvent event) {
        return new EventResponse(
                event.getId(),
                event.getStockCode(),
                event.getEventType(),
                event.getEventSource(),
                event.getEventDate(),
                event.getTitle(),
                event.getDescription()
        );
    }
}

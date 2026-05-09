package com.example.marklong.domain.calendar.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stockCode; // 특정 종목 이벤트면 있음, 시장 전체 이벤트면 null (FOMC)

    private EventType eventType;

    private EventSource eventSource;

    private LocalDateTime eventDate;

    private Long userId;    //  사용자 커스텀 이벤트 아니면 null

    private String title;

    private String description;

    @Builder
    private CalendarEvent(String stockCode, EventType eventType, EventSource eventSource, LocalDateTime eventDate, Long userId, String title, String description) {
        this.stockCode = stockCode;
        this.eventType = eventType;
        this.eventSource = eventSource;
        this.eventDate = eventDate;
        this.userId = userId;
        this.title = title;
        this.description = description;
    }
}

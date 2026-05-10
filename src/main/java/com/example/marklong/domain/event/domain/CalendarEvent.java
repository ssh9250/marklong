package com.example.marklong.domain.event.domain;

import com.example.marklong.global.entity.SoftDeleteEntity;
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
public class CalendarEvent extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stockCode; // 특정 종목 이벤트면 있음, 시장 전체 이벤트면 null (FOMC)

    @Column(nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private EventSource eventSource;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    private Long userId;    //  사용자 커스텀 이벤트 아니면 null

    @Column(nullable = false)
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

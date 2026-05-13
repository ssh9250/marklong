package com.example.marklong.domain.event.dto;

import com.example.marklong.domain.event.domain.EventSource;
import com.example.marklong.domain.event.domain.EventType;

import java.time.LocalDateTime;
import java.util.Set;

public record EventSearchCondition(
        String stockCode,
        // set 필드의 record 에서의 파라미터 바인딩 주의: spring 6 기준 잘 동작한다고는 하지만...
        // todo: 만약 실제 환경에서 문제 생기면 일반 class로 바꿀 것
        Set<EventType> eventType,
        Set<EventSource> eventSource,
        String title,
        String description,
        LocalDateTime from,
        LocalDateTime to
) {
}

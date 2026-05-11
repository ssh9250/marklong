package com.example.marklong.domain.event.repository;

import com.example.marklong.domain.event.domain.EventSource;
import com.example.marklong.domain.event.domain.EventType;
import com.example.marklong.domain.event.dto.EventResponse;
import com.example.marklong.domain.event.dto.EventSearchCondition;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.example.marklong.domain.event.domain.QCalendarEvent.calendarEvent;
import static org.springframework.util.StringUtils.hasText;

@Repository
@RequiredArgsConstructor
public class EventQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<EventResponse> searchEvents(Long userId, EventSearchCondition condition) {
        return queryFactory
                .select(Projections.constructor(EventResponse.class,
                        calendarEvent.id, calendarEvent.stockCode, calendarEvent.eventType, calendarEvent.eventSource, calendarEvent.eventDate, calendarEvent.title, calendarEvent.description))
                .from(calendarEvent)
                .where(
                        stockCodeEq(condition.stockCode()),
                        typeIn(condition.eventType()),
                        sourceIn(condition.eventSource(), userId),
                        titleContains(condition.title()),
                        descriptionContains(condition.description()),
                        eventDateFrom(condition.from()),
                        eventDateTo(condition.to())
                )
                .orderBy(calendarEvent.id.asc())
                .fetch();
    }

    private BooleanExpression stockCodeEq(String stockCode) {
        return hasText(stockCode) ? calendarEvent.stockCode.eq(stockCode) : null;
    }

    private BooleanExpression typeIn(Set<EventType> eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return null;
        }
        return calendarEvent.eventType.in(eventType);
    }

    private BooleanExpression sourceIn(Set<EventSource> eventSource, Long requestUserId) {
        if (eventSource == null || eventSource.isEmpty()) {
            return calendarEvent.eventSource.ne(EventSource.USER)
                    .or(calendarEvent.userId.eq(requestUserId));
        }

        if (!eventSource.contains(EventSource.USER)) {
            return calendarEvent.eventSource.in(eventSource);
        }
        return calendarEvent.eventSource.in(eventSource)
                .and(
                        calendarEvent.eventSource.ne(EventSource.USER)
                                .or(calendarEvent.userId.eq(requestUserId))
                );
    }

    private BooleanExpression titleContains(String title) {
        return hasText(title) ? calendarEvent.title.contains(title) : null;
    }

    private BooleanExpression descriptionContains(String description) {
        return hasText(description) ? calendarEvent.description.contains(description) : null;
    }

    private BooleanExpression eventDateFrom(LocalDateTime from) {
        return from != null ? calendarEvent.eventDate.goe(from) : null;
    }

    private BooleanExpression eventDateTo(LocalDateTime to) {
        return to != null ? calendarEvent.eventDate.between(to, LocalDateTime.now()) : null;
    }
    // eventsource == user and id != userId -->
}

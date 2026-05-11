package com.example.marklong.domain.event.service;

import com.example.marklong.domain.auth.domain.Role;
import com.example.marklong.domain.event.domain.CalendarEvent;
import com.example.marklong.domain.event.domain.EventSource;
import com.example.marklong.domain.event.dto.EventCreateRequest;
import com.example.marklong.domain.event.dto.EventResponse;
import com.example.marklong.domain.event.dto.EventSearchCondition;
import com.example.marklong.domain.event.dto.EventUpdateRequest;
import com.example.marklong.domain.event.repository.EventQueryRepository;
import com.example.marklong.domain.event.repository.EventRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {
    private final EventRepository eventRepository;
    private final EventQueryRepository eventQueryRepository;

    public Long createUserEvent(Long userId, EventCreateRequest request) {
        CalendarEvent calendarEvent = CalendarEvent.builder()
                .stockCode(request.stockCode())
                .eventType(request.eventType())
                .eventSource(EventSource.USER)
                .eventDate(request.eventDate())
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .build();
        eventRepository.save(calendarEvent);
        return calendarEvent.getId();
    }

    public Long createAdminEvent(Long userId, EventCreateRequest request) {
        CalendarEvent calendarEvent = CalendarEvent.builder()
                .stockCode(request.stockCode())
                .eventType(request.eventType())
                .eventSource(EventSource.ADMIN)
                .eventDate(request.eventDate())
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .build();
        eventRepository.save(calendarEvent);
        return calendarEvent.getId();
    }

    public List<EventResponse> searchEvents(Long userId, EventSearchCondition condition) {
        return eventQueryRepository.searchEvents(userId, condition);
    }

    public void update(Long userId, Role role, Long eventId, EventUpdateRequest request) {
        CalendarEvent event = getEventOrThrow(eventId);

        if (role == Role.ROLE_ADMIN) {
            event.update(request.stockCode(), request.eventType(), request.eventDate(), request.title(), request.description());
        } else {
            if (event.getEventSource() != EventSource.USER || !event.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            event.update(request.stockCode(), request.eventType(), request.eventDate(), request.title(), request.description());
        }
    }

    public void delete(Long userId, Role role, Long eventId) {
        CalendarEvent event = getEventOrThrow(eventId);

        if (role == Role.ROLE_ADMIN) {
            event.delete();
        } else {
            if (event.getEventSource() != EventSource.USER || !event.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            event.delete();
        }
    }

    private CalendarEvent getEventOrThrow(Long eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND));
    }
}

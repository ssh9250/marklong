package com.example.marklong.domain.event.service;

import com.example.marklong.domain.event.domain.CalendarEvent;
import com.example.marklong.domain.event.domain.EventSource;
import com.example.marklong.domain.event.dto.EventCreateRequest;
import com.example.marklong.domain.event.dto.EventResponse;
import com.example.marklong.domain.event.dto.EventSearchCondition;
import com.example.marklong.domain.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {
    private final EventRepository eventRepository;

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

    public Long createAdmin(Long userId, EventCreateRequest request) {
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
        return null;
    }
}

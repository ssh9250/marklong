package com.example.marklong.domain.event.repository;


import com.example.marklong.domain.event.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventRepository extends JpaRepository<CalendarEvent, Long> {
    Optional<CalendarEvent> findByIdAndDeletedAtIsNull(long id);
}

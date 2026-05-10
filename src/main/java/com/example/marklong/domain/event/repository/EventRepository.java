package com.example.marklong.domain.event.repository;


import com.example.marklong.domain.event.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<CalendarEvent, Long> {

}

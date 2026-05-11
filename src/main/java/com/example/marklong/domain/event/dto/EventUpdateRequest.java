package com.example.marklong.domain.event.dto;

import com.example.marklong.domain.event.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventUpdateRequest(
        String stockCode,
        @NotNull EventType eventType,
        @NotNull LocalDateTime eventDate,
        @NotBlank String title,
        String description
) {
}

package com.example.marklong.domain.event.controller;

import com.example.marklong.domain.event.dto.EventCreateRequest;
import com.example.marklong.domain.event.dto.EventResponse;
import com.example.marklong.domain.event.dto.EventSearchCondition;
import com.example.marklong.domain.event.dto.EventUpdateRequest;
import com.example.marklong.domain.event.service.EventService;
import com.example.marklong.global.response.ApiResponse;
import com.example.marklong.security.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
@Tag(name = "Event", description = "공시/FOMC/배당일 등 주요 이벤트 API")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @Operation(summary = "이벤트 생성 (유저)")
    public ResponseEntity<ApiResponse<Long>> createUserEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid EventCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.createUserEvent(userDetails.getUserId(), request)));
    }

    @PostMapping("/admin")
    @Operation(summary = "이벤트 생성 (관리자)", description = "관리자 전용 공개 이벤트를 생성합니다.")
    public ResponseEntity<ApiResponse<Long>> createAdminEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid EventCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.createAdminEvent(userDetails.getUserId(), request)));
    }

    @GetMapping
    @Operation(summary = "이벤트 목록 검색", description = "종목 코드·이벤트 유형·출처·기간 조건으로 검색합니다. eventType, eventSource는 반복 파라미터로 복수 전달 가능합니다.")
    public ResponseEntity<ApiResponse<List<EventResponse>>> searchEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute EventSearchCondition condition
    ) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.searchEvents(userDetails.getUserId(), condition)));
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "이벤트 수정", description = "유저는 본인이 생성한 이벤트만, 관리자는 모든 이벤트를 수정할 수 있습니다.")
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId,
            @RequestBody @Valid EventUpdateRequest request
    ) {
        eventService.update(userDetails.getUserId(), userDetails.getRole(), eventId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "이벤트 삭제", description = "유저는 본인이 생성한 이벤트만, 관리자는 모든 이벤트를 삭제할 수 있습니다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId
    ) {
        eventService.delete(userDetails.getUserId(), userDetails.getRole(), eventId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}

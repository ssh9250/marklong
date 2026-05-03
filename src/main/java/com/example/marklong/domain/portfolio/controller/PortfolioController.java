package com.example.marklong.domain.portfolio.controller;

import com.example.marklong.domain.portfolio.dto.*;
import com.example.marklong.domain.portfolio.service.PortfolioService;
import com.example.marklong.global.response.ApiResponse;
import com.example.marklong.security.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolios")
@Tag(name = "Portfolio", description = "포트폴리오 관리 및 종목 배분 API")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping
    @Operation(summary = "포트폴리오 생성")
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PortfolioCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.create(userDetails.getUserId(), request)));
    }

    @GetMapping
    @Operation(summary = "내 포트폴리오 목록 조회")
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> getMyPortfolios(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.getMyPortfolios(userDetails.getUserId())));
    }

    @GetMapping("/{portfolioId}")
    @Operation(summary = "포트폴리오 단건 상세 조회", description = "포트폴리오 요약 정보와 배분된 종목 목록을 함께 반환합니다.")
    public ResponseEntity<ApiResponse<PortfolioDetailResponse>> getOne(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long portfolioId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.getOne(userDetails.getUserId(), portfolioId)));
    }

    @PutMapping("/{portfolioId}")
    @Operation(summary = "포트폴리오 수정", description = "포트폴리오 이름과 설명을 수정합니다.")
    public ResponseEntity<ApiResponse<PortfolioResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long portfolioId,
            @RequestBody PortfolioUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.update(userDetails.getUserId(), portfolioId, request)));
    }

    @DeleteMapping("/{portfolioId}")
    @Operation(summary = "포트폴리오 삭제", description = "포트폴리오와 하위 종목을 모두 삭제하고, 배분된 수량을 보유 자산에 반환합니다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long portfolioId
    ) {
        portfolioService.delete(userDetails.getUserId(), portfolioId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{portfolioId}/items")
    @Operation(summary = "종목 배분", description = "보유 자산에서 특정 수량을 포트폴리오에 배분합니다. 이미 배분된 종목이면 수량을 추가합니다.")
    public ResponseEntity<ApiResponse<PortfolioItemResponse>> allocate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long portfolioId,
            @RequestBody AllocateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.allocate(userDetails.getUserId(), portfolioId, request)));
    }

    @DeleteMapping("/{portfolioId}/items/{itemId}")
    @Operation(summary = "종목 배분 해제", description = "포트폴리오에서 종목을 제거하고 배분 수량을 보유 자산에 반환합니다.")
    public ResponseEntity<ApiResponse<Void>> deallocate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long portfolioId,
            @PathVariable Long itemId
    ) {
        portfolioService.deallocate(userDetails.getUserId(), portfolioId, itemId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}

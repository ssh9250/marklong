package com.example.marklong.domain.holding.controller;

import com.example.marklong.domain.holding.dto.*;
import com.example.marklong.domain.holding.service.HoldingService;
import com.example.marklong.global.response.ApiResponse;
import com.example.marklong.security.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/holdings")
@Tag(name = "Holding", description = "보유 자산 매수/매도 및 조회 API")
public class HoldingController {

    private final HoldingService holdingService;

    @PostMapping("/buy")
    @Operation(summary = "종목 매수", description = "종목 코드와 수량, 단가를 입력해 매수를 기록합니다. 이미 보유 중인 종목이면 평단가를 재계산합니다.")
    public ResponseEntity<ApiResponse<HoldingResponse>> buy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody HoldingBuyRequest request
    ) {
        HoldingResponse response = holdingService.buy(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/sell")
    @Operation(summary = "종목 매도", description = "보유 중인 종목을 매도합니다. 보유 수량이 부족하면 예외가 발생합니다.")
    public ResponseEntity<ApiResponse<HoldingResponse>> sell(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody HoldingSellRequest request
    ) {
        HoldingResponse response = holdingService.sell(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "보유 종목 전체 조회", description = "로그인한 사용자의 보유 종목 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<List<HoldingResponse>>> getMyHoldings(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<HoldingResponse> response = holdingService.getMyHoldings(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{holdingId}")
    @Operation(summary = "보유 종목 단건 조회", description = "특정 보유 종목의 상세 정보를 반환합니다. 현재가 및 미실현손익은 추후 연동 예정입니다.")
    public ResponseEntity<ApiResponse<HoldingDetailResponse>> getHolding(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "보유 종목 ID") @PathVariable Long holdingId
    ) {
        HoldingDetailResponse response = holdingService.getHolding(userDetails.getUserId(), holdingId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/history")
    @Operation(summary = "거래 내역 검색", description = "종목 코드·이름·거래 유형·기간·메모·손익 여부 등 조건으로 거래 내역을 검색합니다. 조건을 생략하면 전체 내역을 반환합니다.")
    public ResponseEntity<ApiResponse<List<HoldingHistoryResponse>>> searchHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "검색 조건 (모든 필드 선택)") @ModelAttribute HistorySearchCondition condition
    ) {
        List<HoldingHistoryResponse> response = holdingService.searchHistory(userDetails.getUserId(), condition);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

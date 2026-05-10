package com.example.marklong.domain.holding.dto;


import java.math.BigDecimal;

public record HoldingDetailResponse(
        Long id,
        String stockCode,
        String stockName,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal quantity,
        BigDecimal unrealizedProfit,
        BigDecimal profitPercentage
        // 주식코드, 이름, 평단가 or 현재가, 수량, 손익가격, 손익 퍼센트
        ) {}

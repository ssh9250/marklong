package com.example.marklong.domain.stock.dto;

import com.example.marklong.domain.stock.domain.Market;

import java.math.BigDecimal;

public record KisCurrentPriceResponse(
        String stockCode,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changeRate,
        Long volume,
        Market market
) {
}

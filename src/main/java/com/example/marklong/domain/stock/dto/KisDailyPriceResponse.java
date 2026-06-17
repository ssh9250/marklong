package com.example.marklong.domain.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KisDailyPriceResponse(
        String stockCode,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {
}

package com.example.marklong.domain.holding.dto;

import com.example.marklong.domain.holding.domain.Holding;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class HoldingResponse {
    private String stockCode;
    private BigDecimal quantity;
    private BigDecimal avgPrice;

    public static HoldingResponse from(Holding holding){
        return HoldingResponse.builder()
                .stockCode(holding.getStockCode())
                .quantity(holding.getTotalQuantity())
                .avgPrice(holding.getAvgPrice())
                .build();
    }
}

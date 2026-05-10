package com.example.marklong.domain.holding.dto;

import com.example.marklong.domain.holding.domain.Holding;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class HoldingResponse {
    private Long id;
    private String stockCode;
    private BigDecimal quantity;
    private BigDecimal avgPrice;
    private String memo;

    public static HoldingResponse from(Holding holding){
        return HoldingResponse.builder()
                .id(holding.getId())
                .stockCode(holding.getStockCode())
                .quantity(holding.getTotalQuantity())
                .avgPrice(holding.getAvgPrice())
                .build();
    }
}

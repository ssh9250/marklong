package com.example.marklong.domain.holding.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class HoldingBuyRequest {
    private String stockCode;
    private BigDecimal quantity;
    private BigDecimal price;
    private String memo;
}

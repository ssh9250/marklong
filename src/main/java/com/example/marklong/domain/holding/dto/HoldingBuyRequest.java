package com.example.marklong.domain.holding.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class HoldingBuyRequest {
    private String stockCode;
    private BigDecimal quantity;
    private BigDecimal price;
}

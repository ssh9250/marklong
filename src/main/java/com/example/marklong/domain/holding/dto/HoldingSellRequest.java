package com.example.marklong.domain.holding.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class HoldingSellRequest {
    private String stockCode;
    private BigDecimal quantity;
    private BigDecimal price;
    private String memo;
}

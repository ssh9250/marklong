package com.example.marklong.domain.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AllocateRequest {
    private String stockCode;
    private BigDecimal quantity;
}

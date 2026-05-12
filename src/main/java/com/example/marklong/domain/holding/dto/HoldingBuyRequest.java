package com.example.marklong.domain.holding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class HoldingBuyRequest {
    @NotBlank
    private String stockCode;
    @NotBlank
    private BigDecimal quantity;
    @NotBlank
    private BigDecimal price;
    private String memo;
}
